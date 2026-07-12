import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Abstraction over persistent token storage so auth code is testable
/// without platform channels.
abstract interface class TokenStorage {
  Future<void> saveTokens({required String accessToken, String? refreshToken});
  Future<String?> readAccessToken();
  Future<String?> readRefreshToken();
  Future<void> clear();
}

/// Stores tokens in the platform keystore/keychain via flutter_secure_storage.
class SecureTokenStorage implements TokenStorage {
  SecureTokenStorage([FlutterSecureStorage? storage])
      : _storage = storage ?? const FlutterSecureStorage();

  static const _accessTokenKey = 'auth_access_token';
  static const _refreshTokenKey = 'auth_refresh_token';

  final FlutterSecureStorage _storage;

  @override
  Future<void> saveTokens({required String accessToken, String? refreshToken}) async {
    await _storage.write(key: _accessTokenKey, value: accessToken);
    if (refreshToken != null) {
      await _storage.write(key: _refreshTokenKey, value: refreshToken);
    }
  }

  @override
  Future<String?> readAccessToken() => _storage.read(key: _accessTokenKey);

  @override
  Future<String?> readRefreshToken() => _storage.read(key: _refreshTokenKey);

  @override
  Future<void> clear() async {
    await _storage.delete(key: _accessTokenKey);
    await _storage.delete(key: _refreshTokenKey);
  }
}
