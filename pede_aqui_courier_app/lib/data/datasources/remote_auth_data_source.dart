import '../../core/auth/oidc_session.dart';
import '../../core/network/api_client.dart';
import 'auth_data_source.dart';

/// Real-backend auth: Keycloak PKCE session on the public client
/// `pede-aqui-courier-mobile` via [OidcSession], courier id from `GET /couriers/me`.
class RemoteAuthDataSource implements AuthDataSource {
  RemoteAuthDataSource(this._apiClient, this._session);

  final ApiClient _apiClient;
  final OidcSession _session;

  @override
  Future<({String accessToken, String courierId})> login() async {
    final accessToken = await _session.signIn();
    _apiClient.setAuthToken(accessToken);
    try {
      final courierId = await _loadCourierId();
      return (accessToken: _session.accessToken ?? accessToken, courierId: courierId);
    } catch (_) {
      // Signed in to Keycloak but not usable here (no courier profile,
      // backend down): do not keep a half-open session around.
      await _dropLocalSession();
      rethrow;
    }
  }

  @override
  Future<({String accessToken, String courierId})?> restoreSession() async {
    final String? accessToken;
    try {
      accessToken = await _session.restore();
    } catch (_) {
      return null; // offline or Keycloak unreachable: stored tokens are kept for the next start
    }
    if (accessToken == null) return null;

    _apiClient.setAuthToken(accessToken);
    try {
      final courierId = await _loadCourierId();
      return (accessToken: _session.accessToken ?? accessToken, courierId: courierId);
    } catch (_) {
      await _dropLocalSession();
      return null;
    }
  }

  /// Refreshes the access token (rotating the refresh token). Wired as the
  /// [ApiClient.onRefreshToken] callback for transparent 401 retries.
  Future<void> refreshAccessToken() async {
    try {
      _apiClient.setAuthToken(await _session.refresh());
    } on SessionExpiredException {
      _apiClient.clearAuthToken();
      rethrow;
    }
  }

  @override
  Future<void> logout() async {
    _apiClient.clearAuthToken();
    await _session.signOut();
  }

  Future<void> _dropLocalSession() async {
    _apiClient.clearAuthToken();
    await _session.clearLocal();
  }

  Future<String> _loadCourierId() async {
    final profileResponse = await _apiClient.get<Map<String, dynamic>>('/couriers/me');
    return profileResponse.data!['id'] as String;
  }
}
