import 'package:flutter_appauth/flutter_appauth.dart';

import '../config/app_config.dart';
import '../storage/token_storage.dart';

/// Keycloak client settings for the Authorization Code + PKCE flow.
class OidcConfig {
  const OidcConfig({
    required this.issuer,
    required this.clientId,
    required this.redirectUri,
    this.scopes = const ['openid', 'profile', 'email'],
  });

  /// Settings from the KEYCLOAK_* dart-defines (see [AppConfig]).
  static const OidcConfig fromEnvironment = OidcConfig(
    issuer: AppConfig.keycloakIssuer,
    clientId: AppConfig.keycloakClientId,
    redirectUri: AppConfig.keycloakRedirectUri,
  );

  final String issuer;
  final String clientId;
  final String redirectUri;
  final List<String> scopes;

  /// AppAuth refuses plain-http issuers unless told otherwise; only local dev
  /// Keycloak runs on http, so this is never true for a real deployment.
  bool get allowInsecureConnections => issuer.startsWith('http://');
}

/// The user closed the browser tab before finishing the Keycloak login.
class AuthCancelledException implements Exception {
  const AuthCancelledException();

  @override
  String toString() => 'AuthCancelledException';
}

/// Keycloak rejected the refresh token (expired, revoked or already rotated):
/// the user has to sign in again.
class SessionExpiredException implements Exception {
  const SessionExpiredException();

  @override
  String toString() => 'SessionExpiredException: Sessão expirou';
}

/// Owns the OIDC session: interactive PKCE sign-in, refresh-token rotation and
/// RP-initiated logout. The access token lives only in memory; the refresh and
/// id tokens are persisted through [TokenStorage] (keychain/keystore).
class OidcSession {
  OidcSession({
    required this.config,
    required TokenStorage storage,
    FlutterAppAuth appAuth = const FlutterAppAuth(),
  })  : _storage = storage,
        _appAuth = appAuth;

  final OidcConfig config;
  final TokenStorage _storage;
  final FlutterAppAuth _appAuth;

  String? _accessToken;
  Future<String>? _refreshInFlight;

  /// Current access token, if a sign-in or refresh succeeded in this process.
  String? get accessToken => _accessToken;

  /// Opens the Keycloak login page in the system browser and exchanges the
  /// returned code (PKCE S256) for tokens. Returns the access token.
  Future<String> signIn({String? loginHint}) async {
    final AuthorizationTokenResponse response;
    try {
      response = await _appAuth.authorizeAndExchangeCode(
        AuthorizationTokenRequest(
          config.clientId,
          config.redirectUri,
          issuer: config.issuer,
          scopes: config.scopes,
          loginHint: loginHint,
          // Always show the login form: if end-session could not reach Keycloak
          // on logout, a lingering SSO cookie must not silently sign the user back in.
          promptValues: const [Prompt.login],
          allowInsecureConnections: config.allowInsecureConnections,
        ),
      );
    } on FlutterAppAuthUserCancelledException {
      throw const AuthCancelledException();
    }
    return _store(response);
  }

  /// Restores the persisted session by refreshing it. Returns null when there
  /// is no stored session or Keycloak no longer accepts it.
  Future<String?> restore() async {
    final refreshToken = await _storage.readRefreshToken();
    if (refreshToken == null || refreshToken.isEmpty) return null;
    try {
      return await refresh();
    } on SessionExpiredException {
      return null;
    }
  }

  /// Exchanges the refresh token for a new token pair. Single-flight: Keycloak
  /// rotates refresh tokens (refreshTokenMaxReuse=0), so a second concurrent
  /// refresh with the same token would fail and log the user out.
  Future<String> refresh() {
    final existing = _refreshInFlight;
    if (existing != null) return existing;
    final refresh = _runRefresh();
    _refreshInFlight = refresh;
    refresh.then((_) {}, onError: (Object _) {}).whenComplete(() {
      if (identical(_refreshInFlight, refresh)) _refreshInFlight = null;
    });
    return refresh;
  }

  Future<String> _runRefresh() async {
    final refreshToken = await _storage.readRefreshToken();
    if (refreshToken == null || refreshToken.isEmpty) {
      _accessToken = null;
      throw const SessionExpiredException();
    }
    final TokenResponse response;
    try {
      response = await _appAuth.token(
        TokenRequest(
          config.clientId,
          config.redirectUri,
          issuer: config.issuer,
          scopes: config.scopes,
          refreshToken: refreshToken,
          grantType: GrantType.refreshToken,
          allowInsecureConnections: config.allowInsecureConnections,
        ),
      );
    } on FlutterAppAuthPlatformException catch (e) {
      // Only a rejected grant ends the session; network errors keep the stored
      // tokens so the next attempt (or app start) can still succeed.
      if (e.platformErrorDetails.error == FlutterAppAuthOAuthError.invalidGrant) {
        await clearLocal();
        throw const SessionExpiredException();
      }
      rethrow;
    }
    return _store(response);
  }

  /// Ends the Keycloak SSO session (best effort, id_token_hint) and always
  /// wipes the local tokens.
  Future<void> signOut() async {
    final idToken = await _storage.readIdToken();
    try {
      if (idToken != null && idToken.isNotEmpty) {
        await _appAuth.endSession(
          EndSessionRequest(
            idTokenHint: idToken,
            postLogoutRedirectUrl: config.redirectUri,
            issuer: config.issuer,
            allowInsecureConnections: config.allowInsecureConnections,
          ),
        );
      }
    } catch (_) {
      // Offline, cancelled or rejected by Keycloak: the local session is still
      // dropped, and the next sign-in forces the login form (prompt=login).
    } finally {
      await clearLocal();
    }
  }

  /// Forgets every token on this device without contacting Keycloak.
  Future<void> clearLocal() async {
    _accessToken = null;
    await _storage.clear();
  }

  Future<String> _store(TokenResponse response) async {
    final accessToken = response.accessToken;
    if (accessToken == null || accessToken.isEmpty) {
      throw StateError('Keycloak não devolveu access token.');
    }
    final refreshToken = response.refreshToken;
    if (refreshToken != null && refreshToken.isNotEmpty) {
      // The rotated refresh token replaces the old one, which is now dead.
      await _storage.saveSession(refreshToken: refreshToken, idToken: response.idToken);
    }
    _accessToken = accessToken;
    return accessToken;
  }
}
