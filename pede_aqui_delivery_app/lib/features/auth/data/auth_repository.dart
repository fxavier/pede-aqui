import 'package:dio/dio.dart';
import '../../../core/config/app_config.dart';
import '../../../core/network/api_client.dart';
import '../../../core/storage/token_storage.dart';

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

abstract class AuthRepository {
  Future<AuthUser> login({required String email, required String password});
  Future<AuthUser> register({required String name, required String email, required String password});
  Future<void> logout();

  /// Restores a previously persisted session, or returns null when none exists.
  Future<AuthUser?> restoreSession();
}

class MockAuthRepository implements AuthRepository {
  @override
  Future<AuthUser> login({required String email, required String password}) async {
    await Future<void>.delayed(const Duration(milliseconds: 450));
    return AuthUser(
      id: 'usr_001',
      name: 'Felix',
      email: email,
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

class ApiAuthRepository implements AuthRepository {
  ApiAuthRepository(this._apiClient, this._tokenStorage, {Dio? authDio})
      : _authDio = authDio ?? Dio();

  final ApiClient _apiClient;
  final TokenStorage _tokenStorage;

  /// Bare Dio for the Keycloak token endpoint (absolute URL, no API base/auth).
  final Dio _authDio;

  String? _refreshToken;
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
  Future<AuthUser> login({required String email, required String password}) async {
    try {
      final tokenResponse = await _authDio.post<Map<String, dynamic>>(
        AppConfig.keycloakTokenEndpoint,
        data: 'grant_type=password&client_id=delivery-app&username=$email&password=$password',
        options: Options(
          contentType: 'application/x-www-form-urlencoded',
          headers: {'Accept': 'application/json'},
        ),
      );

      final tokenData = tokenResponse.data!;
      final accessToken = tokenData['access_token'] as String;
      _refreshToken = tokenData['refresh_token'] as String?;

      _apiClient.setAuthToken(accessToken);
      await _tokenStorage.saveTokens(accessToken: accessToken, refreshToken: _refreshToken);

      return _loadProfile(accessToken, fallbackEmail: email);
    } on DioException catch (e) {
      if (e.response?.statusCode == 401) {
        throw Exception('Credenciais inválidas');
      }
      throw Exception('Erro de autenticação: ${e.message}');
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
      final data = e.response?.data;
      final message = data is Map<String, dynamic>
          ? (data['message'] as String? ?? data['error'] as String?)
          : null;
      throw Exception(message ?? 'Não foi possível criar conta: ${e.message}');
    }
    // Registration returns no tokens, so authenticate right away.
    return login(email: email, password: password);
  }

  @override
  Future<void> logout() async {
    _apiClient.clearAuthToken();
    _refreshToken = null;
    _customerId = null;
    await _tokenStorage.clear();
  }

  Future<void> refreshToken() async {
    if (_refreshToken == null) throw Exception('Sessão expirou');

    try {
      final response = await _authDio.post<Map<String, dynamic>>(
        AppConfig.keycloakTokenEndpoint,
        data: 'grant_type=refresh_token&client_id=delivery-app&refresh_token=$_refreshToken',
        options: Options(
          contentType: 'application/x-www-form-urlencoded',
          headers: {'Accept': 'application/json'},
        ),
      );

      final tokenData = response.data!;
      final accessToken = tokenData['access_token'] as String;
      _refreshToken = tokenData['refresh_token'] as String?;

      _apiClient.setAuthToken(accessToken);
      await _tokenStorage.saveTokens(accessToken: accessToken, refreshToken: _refreshToken);
    } on DioException {
      _refreshToken = null;
      _apiClient.clearAuthToken();
      await _tokenStorage.clear();
      throw Exception('Sessão expirou');
    }
  }

  @override
  Future<AuthUser?> restoreSession() async {
    final accessToken = await _tokenStorage.readAccessToken();
    if (accessToken == null || accessToken.isEmpty) return null;

    _refreshToken = await _tokenStorage.readRefreshToken();
    _apiClient.setAuthToken(accessToken);
    try {
      // On 401 the ApiClient interceptor transparently refreshes and retries.
      return await verifySession();
    } catch (_) {
      _apiClient.clearAuthToken();
      _refreshToken = null;
      _customerId = null;
      await _tokenStorage.clear();
      return null;
    }
  }

  Future<AuthUser> verifySession() async {
    final accessToken = await _tokenStorage.readAccessToken() ?? '';
    return _loadProfile(accessToken);
  }

  Future<AuthUser> _loadProfile(String accessToken, {String? fallbackEmail}) async {
    final response = await _apiClient.get<Map<String, dynamic>>('/me');
    final data = response.data!;
    _customerId = data['keycloakUserId'] as String? ?? data['id'] as String?;
    return AuthUser(
      id: data['id'] as String? ?? data['keycloakUserId'] as String,
      name: data['displayName'] as String? ?? data['name'] as String? ?? '',
      email: data['email'] as String? ?? fallbackEmail ?? '',
      token: accessToken,
    );
  }
}
