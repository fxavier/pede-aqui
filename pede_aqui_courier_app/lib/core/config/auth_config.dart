/// Keycloak settings for the courier app, set per environment via --dart-define.
abstract final class AuthConfig {
  /// Realm issuer. The default targets local Keycloak (port 8081) from an
  /// Android emulator, matching the ApiClient's 10.0.2.2 API default.
  static const String keycloakIssuer = String.fromEnvironment(
    'KEYCLOAK_ISSUER',
    defaultValue: 'http://10.0.2.2:8081/realms/delivery',
  );

  /// Public Keycloak client for couriers (Authorization Code + PKCE only).
  static const String keycloakClientId = String.fromEnvironment(
    'KEYCLOAK_CLIENT_ID',
    defaultValue: 'pede-aqui-courier-mobile',
  );

  /// Must match the client's redirect URI in the realm and the
  /// `appAuthRedirectScheme` (Android) / CFBundleURLSchemes (iOS) scheme.
  static const String keycloakRedirectUri = String.fromEnvironment(
    'KEYCLOAK_REDIRECT_URI',
    defaultValue: 'com.pedeaqui.courier:/oauthredirect',
  );
}
