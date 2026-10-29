import '../datasources/auth_data_source.dart';

abstract interface class AuthRepository {
  /// Signs the courier in through the Keycloak login page (PKCE).
  Future<({String accessToken, String courierId})> login();

  /// Restores a previously persisted session, or returns null when none exists.
  Future<({String accessToken, String courierId})?> restoreSession();

  Future<void> logout();
}

class AuthRepositoryImpl implements AuthRepository {
  AuthRepositoryImpl(this._dataSource);

  final AuthDataSource _dataSource;

  @override
  Future<({String accessToken, String courierId})> login() => _dataSource.login();

  @override
  Future<({String accessToken, String courierId})?> restoreSession() {
    return _dataSource.restoreSession();
  }

  @override
  Future<void> logout() async {
    await _dataSource.logout();
  }
}
