import 'package:dio/dio.dart';

/// A backend error decoded from an RFC 9457 problem body (docs/api/ERROR-CONTRACT.md).
///
/// Clients decide on [code] (stable, snake_case), never on the English `detail` text.
class ApiError implements Exception {
  const ApiError({this.status, this.code, this.detail, this.retryAfter, this.isNetwork = false});

  /// HTTP status, or null when no response arrived (offline, timeout).
  final int? status;

  /// Problem `code` (e.g. `location_consent_required`), or null when the body had none.
  final String? code;
  final String? detail;

  /// Parsed `Retry-After` header (seconds) or `details.retryAfterSeconds` on 429.
  final Duration? retryAfter;

  /// True when the request never got an HTTP response (connection error, timeout).
  final bool isNetwork;

  bool get isRateLimited => status == 429;

  /// Wraps any error thrown by the API layer; non-Dio errors become a generic [ApiError].
  static ApiError from(Object error) {
    if (error is ApiError) return error;
    if (error is! DioException) return ApiError(detail: error.toString());
    final response = error.response;
    if (response == null) {
      return ApiError(isNetwork: true, detail: error.message);
    }
    final body = response.data;
    String? code;
    String? detail;
    int? retryAfterFromBody;
    if (body is Map) {
      code = body['code'] as String?;
      detail = (body['detail'] ?? body['message']) as String?;
      final details = body['details'];
      if (details is Map && details['retryAfterSeconds'] is num) {
        retryAfterFromBody = (details['retryAfterSeconds'] as num).toInt();
      }
    }
    final header = int.tryParse(response.headers.value('retry-after')?.trim() ?? '');
    final seconds = header ?? retryAfterFromBody;
    return ApiError(
      status: response.statusCode,
      code: code,
      detail: detail,
      retryAfter: seconds == null ? null : Duration(seconds: seconds),
    );
  }

  @override
  String toString() => 'ApiError(status: $status, code: $code, detail: $detail)';
}
