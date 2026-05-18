class AppConfig {
  const AppConfig._();

  static const String appName = 'Pede Aqui';
  static const String localeCode = 'pt_MZ';
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8080/api/v1',
  );
}
