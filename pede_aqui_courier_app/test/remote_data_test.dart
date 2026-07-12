import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pede_aqui_courier_app/core/network/api_client.dart';
import 'package:pede_aqui_courier_app/core/storage/token_storage.dart';
import 'package:pede_aqui_courier_app/data/datasources/remote_auth_data_source.dart';
import 'package:pede_aqui_courier_app/data/datasources/remote_courier_data_source.dart';

/// In-memory TokenStorage fake so tests avoid platform channels.
class InMemoryTokenStorage implements TokenStorage {
  String? accessToken;
  String? refreshToken;

  @override
  Future<void> saveTokens({required String accessToken, String? refreshToken}) async {
    this.accessToken = accessToken;
    if (refreshToken != null) this.refreshToken = refreshToken;
  }

  @override
  Future<String?> readAccessToken() async => accessToken;

  @override
  Future<String?> readRefreshToken() async => refreshToken;

  @override
  Future<void> clear() async {
    accessToken = null;
    refreshToken = null;
  }
}

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

Dio testDio(FakeHttpAdapter adapter) {
  final dio = Dio();
  dio.httpClientAdapter = adapter;
  return dio;
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

  group('RemoteAuthDataSource token persistence', () {
    test('login stores access and refresh tokens in secure storage', () async {
      final storage = InMemoryTokenStorage();
      final tokenAdapter = FakeHttpAdapter((_) => jsonResponse({
            'access_token': 'acc-1',
            'refresh_token': 'ref-1',
          }));
      final apiAdapter = FakeHttpAdapter((_) => jsonResponse({'id': 'courier-1'}));
      final dataSource = RemoteAuthDataSource(
        testApiClient(apiAdapter),
        storage,
        authDio: testDio(tokenAdapter),
      );

      final result = await dataSource.login(phone: '+258841234567', password: 'secret123');

      expect(storage.accessToken, 'acc-1');
      expect(storage.refreshToken, 'ref-1');
      expect(result.courierId, 'courier-1');
    });

    test('restoreSession restores the courier from a stored token', () async {
      final storage = InMemoryTokenStorage()..accessToken = 'acc-restored';
      final apiAdapter = FakeHttpAdapter((_) => jsonResponse({'id': 'courier-1'}));
      final dataSource = RemoteAuthDataSource(
        testApiClient(apiAdapter),
        storage,
        authDio: testDio(FakeHttpAdapter((_) => jsonResponse({}))),
      );

      final session = await dataSource.restoreSession();

      expect(session, isNotNull);
      expect(session!.courierId, 'courier-1');
      expect(session.accessToken, 'acc-restored');
      expect(apiAdapter.requests.single.headers['Authorization'], 'Bearer acc-restored');
    });

    test('restoreSession returns null and clears storage on a rejected token', () async {
      final storage = InMemoryTokenStorage()..accessToken = 'acc-expired';
      final apiAdapter = FakeHttpAdapter((_) => jsonResponse({}, status: 401));
      final dataSource = RemoteAuthDataSource(
        testApiClient(apiAdapter),
        storage,
        authDio: testDio(FakeHttpAdapter((_) => jsonResponse({}))),
      );

      expect(await dataSource.restoreSession(), isNull);
      expect(storage.accessToken, isNull);
    });

    test('logout clears persisted tokens', () async {
      final storage = InMemoryTokenStorage()
        ..accessToken = 'acc-1'
        ..refreshToken = 'ref-1';
      final dataSource = RemoteAuthDataSource(
        testApiClient(FakeHttpAdapter((_) => jsonResponse({}))),
        storage,
        authDio: testDio(FakeHttpAdapter((_) => jsonResponse({}))),
      );

      await dataSource.logout();

      expect(storage.accessToken, isNull);
      expect(storage.refreshToken, isNull);
    });
  });
}
