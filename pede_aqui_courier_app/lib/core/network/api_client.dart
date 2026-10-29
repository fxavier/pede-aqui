import 'package:dio/dio.dart';

class ApiClient {
  ApiClient({Dio? dio}) : _dio = dio ?? Dio(BaseOptions(
    baseUrl: _defaultBaseUrl,
    connectTimeout: const Duration(seconds: 15),
    receiveTimeout: const Duration(seconds: 15),
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
  )) {
    _dio.interceptors.add(_createInterceptor());
  }

  static const String _defaultBaseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: 'http://10.0.2.2:8080/api/v1');

  final Dio _dio;
  String? _authToken;

  /// Refreshes the session and sets the new token (wired to the auth data source).
  Future<void> Function()? onRefreshToken;

  /// Called once per failed refresh, however many requests were waiting.
  void Function()? onSessionExpired;

  void setAuthToken(String token) {
    _authToken = token;
  }

  void clearAuthToken() {
    _authToken = null;
  }

  Map<String, dynamic> get _authHeaders {
    if (_authToken != null) {
      return {'Authorization': 'Bearer $_authToken'};
    }
    return {};
  }

  Future<Response<T>> get<T>(String path, {Map<String, dynamic>? queryParameters}) {
    return _dio.get<T>(path, queryParameters: queryParameters, options: Options(headers: _authHeaders));
  }

  Future<Response<T>> post<T>(String path, {Object? data}) {
    return _dio.post<T>(path, data: data, options: Options(headers: _authHeaders));
  }

  Future<Response<T>> patch<T>(String path, {Object? data}) {
    return _dio.patch<T>(path, data: data, options: Options(headers: _authHeaders));
  }

  Future<Response<T>> put<T>(String path, {Object? data}) {
    return _dio.put<T>(path, data: data, options: Options(headers: _authHeaders));
  }

  Future<Response<T>> delete<T>(String path) {
    return _dio.delete<T>(path, options: Options(headers: _authHeaders));
  }

  /// The refresh currently in flight, shared by every request that hits a 401 meanwhile.
  /// Keycloak rotates refresh tokens (refreshTokenMaxReuse=0): two parallel refreshes with the
  /// same token would make the second fail and log the courier out, so only one may run at a time.
  Future<void>? _refreshInFlight;

  static const _retriedKey = 'auth_retried';

  Future<void> _refreshOnce() {
    final existing = _refreshInFlight;
    if (existing != null) return existing;
    final refresh = _runRefresh();
    _refreshInFlight = refresh;
    // Clear the slot once settled; errors are surfaced to the awaiting callers, not here.
    refresh.then((_) {}, onError: (Object _) {}).whenComplete(() {
      if (identical(_refreshInFlight, refresh)) _refreshInFlight = null;
    });
    return refresh;
  }

  Future<void> _runRefresh() async {
    try {
      await onRefreshToken!();
    } catch (_) {
      onSessionExpired?.call();
      rethrow;
    }
  }

  InterceptorsWrapper _createInterceptor() {
    return InterceptorsWrapper(
      onError: (error, handler) async {
        final request = error.requestOptions;
        if (error.response?.statusCode != 401 ||
            onRefreshToken == null ||
            request.extra[_retriedKey] == true) {
          return handler.next(error);
        }
        // Refresh only if no other request already did it since this one was sent.
        final sentWith = request.headers['Authorization'];
        if (_authToken == null || sentWith == 'Bearer $_authToken') {
          try {
            await _refreshOnce();
          } catch (_) {
            return handler.next(error);
          }
        }
        request.headers['Authorization'] = 'Bearer $_authToken';
        request.extra[_retriedKey] = true;
        try {
          return handler.resolve(await _dio.fetch(request));
        } on DioException catch (retryError) {
          return handler.next(retryError);
        }
      },
    );
  }
}
