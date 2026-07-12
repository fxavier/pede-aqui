import '../datasources/auth_data_source.dart';

abstract interface class AuthRepository {
  Future<({String accessToken, String courierId})> login({
    required String phone,
    required String password,
  });

  /// Restores a previously persisted session, or returns null when none exists.
  Future<({String accessToken, String courierId})?> restoreSession();

  Future<void> logout();
}

class AuthRepositoryImpl implements AuthRepository {
  AuthRepositoryImpl(this._dataSource);

  final AuthDataSource _dataSource;

  @override
  Future<({String accessToken, String courierId})> login({
    required String phone,
    required String password,
  }) async {
    final result = await _dataSource.login(phone: phone, password: password);
    return (
      accessToken: result.accessToken,
      courierId: result.courierId,
    );
  }

  @override
  Future<({String accessToken, String courierId})?> restoreSession() {
    return _dataSource.restoreSession();
  }

  @override
  Future<void> logout() async {
    await _dataSource.logout();
  }
}