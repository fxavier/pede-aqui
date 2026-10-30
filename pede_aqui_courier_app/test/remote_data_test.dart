import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pede_aqui_courier_app/core/network/api_client.dart';
import 'package:pede_aqui_courier_app/data/datasources/remote_courier_data_source.dart';

/// Routes Dio requests to canned responses and records them for assertions.
class FakeHttpAdapter implements HttpClientAdapter {
  FakeHttpAdapter(this.handler);

  final ResponseBody Function(RequestOptions options) handler;
  final List<RequestOptions> requests = [];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return handler(options);
  }

  @override
  void close({bool force = false}) {}
}

ResponseBody jsonResponse(Object body, {int status = 200}) {
  return ResponseBody.fromString(
    jsonEncode(body),
    status,
    headers: {
      Headers.contentTypeHeader: [Headers.jsonContentType],
    },
  );
}

ApiClient testApiClient(FakeHttpAdapter adapter) {
  final dio = Dio(BaseOptions(baseUrl: 'http://test.local/api/v1'));
  dio.httpClientAdapter = adapter;
  return ApiClient(dio: dio);
}

void main() {
  group('earnings parsing', () {
    test('getEarningSummary parses the real backend summary shape', () async {
      // Backend CourierEarningsSummaryResponse:
      // { completedDeliveries, failedDeliveries, earningsTotalMzn }
      final adapter = FakeHttpAdapter((_) => jsonResponse({
            'completedDeliveries': 7,
            'failedDeliveries': 1,
            'earningsTotalMzn': 2500.0,
          }));
      final dataSource = RemoteCourierDataSource(testApiClient(adapter));

      final summary = await dataSource.getEarningSummary();

      expect(summary.completedDeliveries, 7);
      // No per-period breakdown exists in the API yet.
      expect(summary.today, 0);
      expect(summary.week, 0);
      expect(summary.month, 0);
    });

    test('getWeeklyEarnings returns empty series without swallowing the summary object', () async {
      final adapter = FakeHttpAdapter((_) => jsonResponse({
            'completedDeliveries': 7,
            'failedDeliveries': 1,
            'earningsTotalMzn': 2500.0,
          }));
      final dataSource = RemoteCourierDataSource(testApiClient(adapter));

      expect(await dataSource.getWeeklyEarnings(), isEmpty);
      expect(adapter.requests.single.path, contains('/couriers/me/earnings-summary'));
    });

    test('getWeeklyEarnings surfaces backend errors instead of returning []', () async {
      final adapter =
          FakeHttpAdapter((_) => jsonResponse({'message': 'boom'}, status: 500));
      final dataSource = RemoteCourierDataSource(testApiClient(adapter));

      expect(dataSource.getWeeklyEarnings(), throwsA(isA<DioException>()));
    });

    test('getEarningHistory returns the honest empty state (no backend endpoint)', () async {
      final adapter = FakeHttpAdapter((_) => jsonResponse({}));
      final dataSource = RemoteCourierDataSource(testApiClient(adapter));

      expect(await dataSource.getEarningHistory(), isEmpty);
      expect(adapter.requests, isEmpty);
    });
  });
}
