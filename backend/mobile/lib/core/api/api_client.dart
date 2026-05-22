import 'package:http/http.dart' as http;

/// Simple API client that attaches bearer tokens to backend requests.
class ApiClient {
  ApiClient({required this.baseUrl, http.Client? httpClient}) : _httpClient = httpClient ?? http.Client();

  final String baseUrl;
  final http.Client _httpClient;

  Future<http.Response> get(String path, String token) {
    return _httpClient.get(
      Uri.parse('$baseUrl$path'),
      headers: {'Authorization': 'Bearer $token'},
    );
  }

  Future<http.Response> post(String path, String token, {Object? body}) {
    return _httpClient.post(
      Uri.parse('$baseUrl$path'),
      headers: {
        'Authorization': 'Bearer $token',
        'Content-Type': 'application/json',
      },
      body: body,
    );
  }

  Future<http.Response> patch(String path, String token, {Object? body}) {
    return _httpClient.patch(
      Uri.parse('$baseUrl$path'),
      headers: {
        'Authorization': 'Bearer $token',
        'Content-Type': 'application/json',
      },
      body: body,
    );
  }
}
