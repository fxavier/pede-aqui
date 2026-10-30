class AppConfig {
  const AppConfig._();

  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8080/api/v1',
  );

  /// Keycloak realm issuer, configurable per environment via --dart-define.
  /// Local Keycloak listens on 8081 (docker compose).
  static const String keycloakIssuer = String.fromEnvironment(
    'KEYCLOAK_ISSUER',
    defaultValue: 'http://localhost:8081/realms/delivery',
  );

  /// Public Keycloak client for this app (Authorization Code + PKCE only).
  static const String keycloakClientId = String.fromEnvironment(
    'KEYCLOAK_CLIENT_ID',
    defaultValue: 'pede-aqui-mobile',
  );

  /// Must match the client's redirect URI in the realm and the
  /// `appAuthRedirectScheme` (Android) / CFBundleURLSchemes (iOS) scheme.
  static const String keycloakRedirectUri = String.fromEnvironment(
    'KEYCLOAK_REDIRECT_URI',
    defaultValue: 'com.pedeaqui.delivery:/oauthredirect',
  );

  static const Duration requestTimeout = Duration(seconds: 20);
}
