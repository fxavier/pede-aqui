abstract interface class AuthDataSource {
  /// Signs the courier in (Keycloak Authorization Code + PKCE in the real
  /// implementation) and resolves their courier id.
  Future<({String accessToken, String courierId})> login();

  /// Restores a previously persisted session, or returns null when none exists.
  Future<({String accessToken, String courierId})?> restoreSession();

  Future<void> logout();
}
