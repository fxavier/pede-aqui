import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Persists the long-lived OIDC tokens (refresh + id token). The access token
/// is short-lived and kept in memory only, so it is never written here.
abstract class TokenStorage {
  /// Saves the (rotated) refresh token; [idToken] is kept when null.
  Future<void> saveSession({required String refreshToken, String? idToken});
  Future<String?> readRefreshToken();
  Future<String?> readIdToken();
  Future<void> clear();
}

/// Stores tokens in the platform keystore/keychain via flutter_secure_storage.
class SecureTokenStorage implements TokenStorage {
  SecureTokenStorage([FlutterSecureStorage? storage])
      : _storage = storage ?? const FlutterSecureStorage();

  /// Written by the old password-grant login; only deleted now.
  static const _legacyAccessTokenKey = 'auth_access_token';
  static const _refreshTokenKey = 'auth_refresh_token';
  static const _idTokenKey = 'auth_id_token';

  final FlutterSecureStorage _storage;

  @override
  Future<void> saveSession({required String refreshToken, String? idToken}) async {
    await _storage.write(key: _refreshTokenKey, value: refreshToken);
    if (idToken != null) {
      await _storage.write(key: _idTokenKey, value: idToken);
    }
  }

  @override
  Future<String?> readRefreshToken() => _storage.read(key: _refreshTokenKey);

  @override
  Future<String?> readIdToken() => _storage.read(key: _idTokenKey);

  @override
  Future<void> clear() async {
    await _storage.delete(key: _legacyAccessTokenKey);
    await _storage.delete(key: _refreshTokenKey);
    await _storage.delete(key: _idTokenKey);
  }
}
