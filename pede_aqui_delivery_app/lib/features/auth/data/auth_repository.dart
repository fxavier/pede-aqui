import 'package:dio/dio.dart';

import '../../../core/auth/oidc_session.dart';
import '../../../core/network/api_client.dart';

class AuthUser {
  const AuthUser({
    required this.id,
    required this.name,
    required this.email,
    required this.token,
  });

  final String id;
  final String name;
  final String email;
  final String token;
}

/// Registration refused by the backend, with a message safe to show the user.
class RegistrationException implements Exception {
  const RegistrationException(this.message);

  final String message;

  @override
  String toString() => 'RegistrationException: $message';
}

abstract class AuthRepository {
  /// Signs in through the Keycloak login page (Authorization Code + PKCE).
  /// [loginHint] pre-fills the username field.
  Future<AuthUser> login({String? loginHint});

  /// Creates the customer account on the backend, then signs in via PKCE.
  Future<AuthUser> register({required String name, required String email, required String password});
  Future<void> logout();

  /// Restores a previously persisted session, or returns null when none exists.
  Future<AuthUser?> restoreSession();
}

/// Demo-mode repository (USE_MOCK_DATA=true): no Keycloak, no backend.
class MockAuthRepository implements AuthRepository {
  @override
  Future<AuthUser> login({String? loginHint}) async {
    await Future<void>.delayed(const Duration(milliseconds: 450));
    return AuthUser(
      id: 'usr_001',
      name: 'Felix',
      email: loginHint ?? 'felix@pedeaqui.co.mz',
      token: 'mock.jwt.token',
    );
  }

  @override
  Future<AuthUser> register({required String name, required String email, required String password}) async {
    await Future<void>.delayed(const Duration(milliseconds: 450));
    return AuthUser(
      id: 'usr_002',
      name: name,
      email: email,
      token: 'mock.jwt.token',
    );
  }

  @override
  Future<void> logout() async {
    await Future<void>.delayed(const Duration(milliseconds: 100));
  }

  @override
  Future<AuthUser?> restoreSession() async => null;
}

/// Real-backend repository: Keycloak PKCE session via [OidcSession], customer
/// profile from `GET /me`, registration via `POST /customers/register`.
class ApiAuthRepository implements AuthRepository {
  ApiAuthRepository(this._apiClient, this._session);

  final ApiClient _apiClient;
  final OidcSession _session;

  String? _customerId;

  /// Customer id used by /customers/{customerId}/... paths. Mirrors the web
  /// app, which uses the Keycloak user id (JWT sub) for these routes; the
  /// backend resolves cart ownership from the JWT, not the path segment.
  String get customerId {
    final id = _customerId;
    if (id == null || id.isEmpty) {
      throw StateError('Sessão não autenticada: customerId indisponível. Faça login primeiro.');
    }
    return id;
  }

  /// Splits a single full-name field into the firstName/lastName pair required
  /// by POST /customers/register (both fields are mandatory server-side).
  static Map<String, String> buildRegistrationPayload({
    required String name,
    required String email,
    required String password,
  }) {
    final parts = name.trim().split(RegExp(r'\s+'));
    final firstName = parts.first;
    final lastName = parts.length > 1 ? parts.sublist(1).join(' ') : parts.first;
    return {
      'firstName': firstName,
      'lastName': lastName,
      'email': email,
      'password': password,
    };
  }

  @override
  Future<AuthUser> login({String? loginHint}) async {
    final accessToken = await _session.signIn(loginHint: loginHint);
    _apiClient.setAuthToken(accessToken);
    try {
      return await _loadProfile(accessToken, fallbackEmail: loginHint);
    } catch (_) {
      // Signed in to Keycloak but unusable here (no profile, backend down):
      // do not keep a half-open session around.
      await _dropLocalSession();
      rethrow;
    }
  }

  @override
  Future<AuthUser> register({required String name, required String email, required String password}) async {
    try {
      await _apiClient.post<Map<String, dynamic>>(
        '/customers/register',
        data: buildRegistrationPayload(name: name, email: email, password: password),
      );
    } on DioException catch (e) {
      throw RegistrationException(_registrationMessage(e));
    }
    // Registration returns no tokens: continue on the Keycloak login page with
    // the e-mail pre-filled (the password is typed there, never sent by the app).
    return login(loginHint: email);
  }

  @override
  Future<void> logout() async {
    _apiClient.clearAuthToken();
    _customerId = null;
    await _session.signOut();
  }

  /// Refreshes the access token (rotating the refresh token). Wired as the
  /// [ApiClient.onRefreshToken] callback for transparent 401 retries.
  Future<void> refreshToken() async {
    try {
      final accessToken = await _session.refresh();
      _apiClient.setAuthToken(accessToken);
    } on SessionExpiredException {
      _apiClient.clearAuthToken();
      _customerId = null;
      rethrow;
    }
  }

  @override
  Future<AuthUser?> restoreSession() async {
    final String? accessToken;
    try {
      accessToken = await _session.restore();
    } catch (_) {
      return null; // offline or Keycloak unreachable: stored tokens are kept for the next start
    }
    if (accessToken == null) return null;

    _apiClient.setAuthToken(accessToken);
    try {
      return await _loadProfile(accessToken);
    } catch (_) {
      await _dropLocalSession();
      return null;
    }
  }

  Future<void> _dropLocalSession() async {
    _apiClient.clearAuthToken();
    _customerId = null;
    await _session.clearLocal();
  }

  Future<AuthUser> _loadProfile(String accessToken, {String? fallbackEmail}) async {
    final response = await _apiClient.get<Map<String, dynamic>>('/me');
    final data = response.data!;
    _customerId = data['keycloakUserId'] as String? ?? data['id'] as String?;
    return AuthUser(
      id: data['id'] as String? ?? data['keycloakUserId'] as String,
      name: data['displayName'] as String? ?? data['name'] as String? ?? '',
      email: data['email'] as String? ?? fallbackEmail ?? '',
      // The ApiClient may have refreshed during /me; report the live token.
      token: _session.accessToken ?? accessToken,
    );
  }

  static String _registrationMessage(DioException e) {
    final data = e.response?.data;
    if (data is Map<String, dynamic>) {
      final code = data['code'] as String?;
      if (code == 'password_policy_violation' ||
          (code == 'validation_failed' && _hasFieldError(data, 'password'))) {
        return 'A palavra-passe não cumpre a política de segurança.';
      }
      final detail = data['detail'] as String? ?? data['message'] as String?;
      if (detail != null && detail.isNotEmpty) return detail;
    }
    return 'Não foi possível criar conta.';
  }

  static bool _hasFieldError(Map<String, dynamic> data, String field) {
    final errors = data['fieldErrors'];
    return errors is List && errors.any((e) => e is Map && e['field'] == field);
  }
}
