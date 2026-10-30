import 'auth_data_source.dart';

/// Demo-mode auth (USE_MOCK_DATA=true): no Keycloak, no backend.
class MockAuthDataSource implements AuthDataSource {
  @override
  Future<({String accessToken, String courierId})> login() async {
    await Future.delayed(const Duration(milliseconds: 300));
    return (
      accessToken: 'mock.jwt.token',
      courierId: 'courier-001',
    );
  }

  @override
  Future<({String accessToken, String courierId})?> restoreSession() async => null;

  @override
  Future<void> logout() async {
    await Future.delayed(const Duration(milliseconds: 100));
  }
}
