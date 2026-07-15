class AppConfig {
  const AppConfig._();

  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8080/api/v1',
  );

  /// Keycloak realm issuer, configurable per environment via --dart-define.
  /// The default preserves the previous hardcoded localhost behaviour for dev.
  static const String keycloakIssuer = String.fromEnvironment(
    'KEYCLOAK_ISSUER',
    defaultValue: 'http://localhost:8080/realms/delivery',
  );

  static const String keycloakTokenEndpoint =
      '$keycloakIssuer/protocol/openid-connect/token';

  static const Duration requestTimeout = Duration(seconds: 20);
}
