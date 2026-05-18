import 'dart:convert';

import 'package:http/http.dart' as http;

class ApiClient {
  ApiClient({required this.baseUrl, required this.token, http.Client? httpClient})
      : _httpClient = httpClient ?? http.Client();

  final String baseUrl;
  final String token;
  final http.Client _httpClient;

  Future<ApiResult<dynamic>> getJson(String path) async {
    final response = await _httpClient.get(Uri.parse('$baseUrl$path'), headers: {'Authorization': 'Bearer $token'});
    return _map(response);
  }

  Future<ApiResult<dynamic>> patchJson(String path, Map<String, dynamic> body) async {
    final response = await _httpClient.patch(
      Uri.parse('$baseUrl$path'),
      headers: {'Authorization': 'Bearer $token', 'Content-Type': 'application/json'},
      body: jsonEncode(body),
    );
    return _map(response);
  }

  Future<ApiResult<dynamic>> postJson(String path, Map<String, dynamic> body) async {
    final response = await _httpClient.post(
      Uri.parse('$baseUrl$path'),
      headers: {'Authorization': 'Bearer $token', 'Content-Type': 'application/json'},
      body: jsonEncode(body),
    );
    return _map(response);
  }

  ApiResult<dynamic> _map(http.Response response) {
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return ApiResult.success(response.body.isEmpty ? {} : jsonDecode(response.body));
    }
    if (response.statusCode == 403) return ApiResult.forbidden();
    return ApiResult.error('Falha de API: ${response.statusCode}');
  }
}

class ApiResult<T> {
  ApiResult._({this.data, this.error, this.forbidden = false});
  final T? data;
  final String? error;
  final bool forbidden;
  bool get ok => error == null && !forbidden;
  factory ApiResult.success(T data) => ApiResult._(data: data);
  factory ApiResult.error(String error) => ApiResult._(error: error);
  factory ApiResult.forbidden() => ApiResult._(forbidden: true);
}
