import 'package:dio/dio.dart';
import '../../core/network/api_client.dart';
import '../../core/storage/token_storage.dart';
import 'auth_data_source.dart';

class RemoteAuthDataSource implements AuthDataSource {
  RemoteAuthDataSource(this._apiClient, this._tokenStorage, {Dio? authDio})
      : _authDio = authDio ?? Dio();

  final ApiClient _apiClient;
  final TokenStorage _tokenStorage;

  /// Bare Dio for the Keycloak token endpoint (absolute URL, no API base/auth).
  final Dio _authDio;

  @override
  Future<({String accessToken, String refreshToken, String courierId})> login({
    required String phone,
    required String password,
  }) async {
    try {
      final tokenResponse = await _authDio.post<Map<String, dynamic>>(
        ApiClient.keycloakTokenEndpoint,
        data: 'grant_type=password&client_id=courier-app&username=$phone&password=$password',
        options: Options(
          contentType: 'application/x-www-form-urlencoded',
          headers: {'Accept': 'application/json'},
        ),
      );

      final tokenData = tokenResponse.data!;
      final accessToken = tokenData['access_token'] as String;
      final refreshToken = tokenData['refresh_token'] as String;

      _apiClient.setAuthToken(accessToken);
      await _tokenStorage.saveTokens(accessToken: accessToken, refreshToken: refreshToken);

      final profileResponse = await _apiClient.get<Map<String, dynamic>>('/couriers/me');
      final profileData = profileResponse.data!;

      return (
        accessToken: accessToken,
        refreshToken: refreshToken,
        courierId: profileData['id'] as String,
      );
    } on DioException catch (e) {
      if (e.response?.statusCode == 401) {
        throw Exception('Credenciais inválidas');
      }
      throw Exception('Erro de autenticação: ${e.message}');
    }
  }

  @override
  Future<({String accessToken, String courierId})?> restoreSession() async {
    final accessToken = await _tokenStorage.readAccessToken();
    if (accessToken == null || accessToken.isEmpty) return null;

    _apiClient.setAuthToken(accessToken);
    try {
      final profileResponse = await _apiClient.get<Map<String, dynamic>>('/couriers/me');
      final profileData = profileResponse.data!;
      return (
        accessToken: accessToken,
        courierId: profileData['id'] as String,
      );
    } catch (_) {
      // Stored token is no longer valid: drop it and require a fresh login.
      _apiClient.clearAuthToken();
      await _tokenStorage.clear();
      return null;
    }
  }

  @override
  Future<void> logout() async {
    _apiClient.clearAuthToken();
    await _tokenStorage.clear();
  }
}
