abstract interface class AuthDataSource {
  Future<({String accessToken, String refreshToken, String courierId})> login({
    required String phone,
    required String password,
  });

  /// Restores a previously persisted session, or returns null when none exists.
  Future<({String accessToken, String courierId})?> restoreSession();

  Future<void> logout();
}
