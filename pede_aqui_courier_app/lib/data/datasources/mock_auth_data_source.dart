import 'auth_data_source.dart';

class MockAuthDataSource implements AuthDataSource {
  @override
  Future<({String accessToken, String refreshToken, String courierId})> login({
    required String phone,
    required String password,
  }) async {
    await Future.delayed(const Duration(milliseconds: 300));
    
    if (!phone.startsWith('+258')) {
      throw Exception('Número de telefone deve começar com +258');
    }
    
    return (
      accessToken: 'mock.jwt.token',
      refreshToken: 'mock.refresh.token',
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