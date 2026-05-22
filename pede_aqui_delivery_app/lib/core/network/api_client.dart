import 'package:dio/dio.dart';

import '../config/app_config.dart';

class ApiClient {
  ApiClient()
      : _dio = Dio(
          BaseOptions(
            baseUrl: AppConfig.apiBaseUrl,
            connectTimeout: AppConfig.requestTimeout,
            receiveTimeout: AppConfig.requestTimeout,
            headers: const {
              'Content-Type': 'application/json',
              'Accept': 'application/json',
            },
          ),
        ) {
    _dio.interceptors.add(_createInterceptor());
  }

  final Dio _dio;
  String? _authToken;
  Future<void> Function()? onRefreshToken;
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

  Future<Response<T>> get<T>(String path, {Map<String, dynamic>? query}) {
    return _dio.get<T>(path, queryParameters: query, options: Options(headers: _authHeaders));
  }

  Future<Response<T>> post<T>(String path, {Object? data}) {
    return _dio.post<T>(path, data: data, options: Options(headers: _authHeaders));
  }

  Future<Response<T>> put<T>(String path, {Object? data}) {
    return _dio.put<T>(path, data: data, options: Options(headers: _authHeaders));
  }

  Future<Response<T>> patch<T>(String path, {Object? data}) {
    return _dio.patch<T>(path, data: data, options: Options(headers: _authHeaders));
  }

  Future<Response<T>> delete<T>(String path) {
    return _dio.delete<T>(path, options: Options(headers: _authHeaders));
  }

  InterceptorsWrapper _createInterceptor() {
    return InterceptorsWrapper(
      onError: (error, handler) async {
        if (error.response?.statusCode == 401 && onRefreshToken != null) {
          try {
            await onRefreshToken!();
            
            final request = error.requestOptions;
            request.headers['Authorization'] = 'Bearer $_authToken';
            
            final response = await _dio.fetch(request);
            return handler.resolve(response);
          } catch (e) {
            onSessionExpired?.call();
            return handler.next(error);
          }
        }
        return handler.next(error);
      },
    );
  }
}
