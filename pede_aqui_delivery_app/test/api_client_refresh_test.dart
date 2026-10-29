import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pede_aqui_mobile/core/network/api_client.dart';

/// Answers 401 unless the request carries the currently valid token.
class TokenCheckingAdapter implements HttpClientAdapter {
  TokenCheckingAdapter(this.validToken);

  String validToken;
  int calls = 0;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    calls++;
    final ok = options.headers['Authorization'] == 'Bearer $validToken';
    return ResponseBody.fromString(
      jsonEncode(ok ? {'path': options.path} : {'code': 'unauthorized'}),
      ok ? 200 : 401,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  late TokenCheckingAdapter adapter;
  late ApiClient client;

  setUp(() {
    adapter = TokenCheckingAdapter('new-token');
    client = ApiClient(dio: Dio(BaseOptions(baseUrl: 'http://api.test'))..httpClientAdapter = adapter);
    client.setAuthToken('expired-token');
  });

  test('concurrent 401s share one refresh and all retries succeed', () async {
    var refreshes = 0;
    final gate = Completer<void>();
    client.onRefreshToken = () async {
      refreshes++;
      await gate.future; // keep the refresh in flight while the other 401s arrive
      client.setAuthToken('new-token');
    };

    final pending = [for (var i = 0; i < 3; i++) client.get<Map<String, dynamic>>('/r$i')];
    await Future<void>.delayed(const Duration(milliseconds: 20));
    gate.complete();
    final responses = await Future.wait(pending);

    expect(refreshes, 1);
    expect(responses.map((r) => r.statusCode), everyElement(200));
  });

  test('a failed refresh expires the session once and fails every waiting request', () async {
    var refreshes = 0;
    var expired = 0;
    client.onRefreshToken = () async {
      refreshes++;
      await Future<void>.delayed(const Duration(milliseconds: 10));
      throw Exception('Sessão expirou');
    };
    client.onSessionExpired = () => expired++;

    final results = await Future.wait([
      for (var i = 0; i < 3; i++)
        client.get<Map<String, dynamic>>('/r$i').then((_) => 'ok', onError: (Object e) => 'failed'),
    ]);

    expect(refreshes, 1);
    expect(expired, 1);
    expect(results, everyElement('failed'));
  });

  test('a 401 after the retry is not refreshed again (no loop)', () async {
    var refreshes = 0;
    client.onRefreshToken = () async {
      refreshes++;
      client.setAuthToken('still-wrong');
    };

    await expectLater(client.get<Map<String, dynamic>>('/r'), throwsA(isA<DioException>()));
    expect(refreshes, 1);
    expect(adapter.calls, 2);
  });

  test('a later refresh starts a new flight once the previous one settled', () async {
    var refreshes = 0;
    client.onRefreshToken = () async {
      refreshes++;
      client.setAuthToken('new-token');
    };
    await client.get<Map<String, dynamic>>('/a');
    client.setAuthToken('expired-again');
    adapter.validToken = 'newer-token';
    client.onRefreshToken = () async {
      refreshes++;
      client.setAuthToken('newer-token');
    };
    final response = await client.get<Map<String, dynamic>>('/b');

    expect(response.statusCode, 200);
    expect(refreshes, 2);
  });
}
