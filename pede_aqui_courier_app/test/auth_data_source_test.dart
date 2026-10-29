import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_appauth/flutter_appauth.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pede_aqui_courier_app/core/auth/oidc_session.dart';
import 'package:pede_aqui_courier_app/core/network/api_client.dart';
import 'package:pede_aqui_courier_app/data/datasources/remote_auth_data_source.dart';

import 'support/auth_fakes.dart';

/// Answers `/couriers/me` only for the currently valid bearer, 401 otherwise.
class CourierApiAdapter implements HttpClientAdapter {
  CourierApiAdapter(this.validToken, {this.status = 200});

  String validToken;
  int status;
  final List<RequestOptions> requests = [];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    final ok = options.headers['Authorization'] == 'Bearer $validToken';
    return ResponseBody.fromString(
      jsonEncode(ok ? {'id': 'courier-1'} : {'code': 'unauthorized'}),
      ok ? status : 401,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

class Harness {
  Harness({String validToken = 'acc-1', int status = 200})
      : adapter = CourierApiAdapter(validToken, status: status) {
    apiClient = ApiClient(
      dio: Dio(BaseOptions(baseUrl: 'http://test.local/api/v1'))..httpClientAdapter = adapter,
    );
    session = OidcSession(config: testOidcConfig, storage: storage, appAuth: appAuth);
    dataSource = RemoteAuthDataSource(apiClient, session);
    apiClient.onRefreshToken = dataSource.refreshAccessToken;
  }

  final CourierApiAdapter adapter;
  final storage = InMemoryTokenStorage();
  final appAuth = FakeAppAuth();
  late final ApiClient apiClient;
  late final OidcSession session;
  late final RemoteAuthDataSource dataSource;
}

void main() {
  group('login (Authorization Code + PKCE)', () {
    test('uses the courier PKCE client and maps the tokens', () async {
      final h = Harness();

      final result = await h.dataSource.login();

      final request = h.appAuth.authorizeRequests.single;
      expect(request.clientId, 'pede-aqui-courier-mobile');
      expect(request.redirectUrl, 'com.pedeaqui.courier:/oauthredirect');
      expect(request.issuer, 'http://keycloak.test/realms/delivery');
      expect(request.promptValues, [Prompt.login]);
      expect(result.accessToken, 'acc-1');
      expect(result.courierId, 'courier-1');
      expect(h.storage.refreshToken, 'ref-1');
      expect(h.storage.idToken, 'id-1');
      expect(h.adapter.requests.single.path, '/couriers/me');
      expect(h.adapter.requests.single.headers['Authorization'], 'Bearer acc-1');
    });

    test('closing the browser surfaces AuthCancelledException', () async {
      final h = Harness();
      h.appAuth.onAuthorize = (_) async => throw userCancelled();

      await expectLater(h.dataSource.login(), throwsA(isA<AuthCancelledException>()));
      expect(h.storage.refreshToken, isNull);
    });

    test('an account without courier profile does not keep the session', () async {
      final h = Harness(status: 404);

      await expectLater(h.dataSource.login(), throwsA(isA<DioException>()));
      expect(h.storage.refreshToken, isNull);
      expect(h.session.accessToken, isNull);
    });
  });

  group('refresh with rotation', () {
    test('concurrent 401s trigger a single refresh and store the rotated token', () async {
      final h = Harness(validToken: 'acc-2');
      h.storage.refreshToken = 'ref-1';
      h.apiClient.setAuthToken('acc-expired');
      final gate = Completer<void>();
      h.appAuth.onToken = (_) async {
        await gate.future;
        return tokenResponse(access: 'acc-2', refresh: 'ref-2', id: 'id-2');
      };

      final pending = [
        for (var i = 0; i < 3; i++) h.apiClient.get<Map<String, dynamic>>('/couriers/me'),
      ];
      await Future<void>.delayed(const Duration(milliseconds: 20));
      gate.complete();
      final responses = await Future.wait(pending);

      expect(responses.map((r) => r.statusCode), everyElement(200));
      expect(h.appAuth.tokenRequests, hasLength(1));
      expect(h.appAuth.tokenRequests.single.refreshToken, 'ref-1');
      expect(h.appAuth.tokenRequests.single.grantType, GrantType.refreshToken);
      expect(h.storage.refreshToken, 'ref-2');
    });

    test('a rejected refresh expires the session once and wipes storage', () async {
      final h = Harness(validToken: 'never');
      h.storage.refreshToken = 'ref-dead';
      h.apiClient.setAuthToken('acc-expired');
      final gate = Completer<void>();
      h.appAuth.onToken = (_) async {
        await gate.future; // keep the refresh in flight while the other 401s arrive
        throw oauthError('invalid_grant');
      };
      var expired = 0;
      h.apiClient.onSessionExpired = () => expired++;

      final pending = Future.wait([
        for (var i = 0; i < 3; i++)
          h.apiClient.get<Map<String, dynamic>>('/couriers/me').then((_) => 'ok', onError: (Object _) => 'failed'),
      ]);
      await Future<void>.delayed(const Duration(milliseconds: 20));
      gate.complete();
      final results = await pending;

      expect(results, everyElement('failed'));
      expect(h.appAuth.tokenRequests, hasLength(1));
      expect(expired, 1);
      expect(h.storage.refreshToken, isNull);
    });
  });

  group('restoreSession', () {
    test('returns null without contacting Keycloak when nothing is persisted', () async {
      final h = Harness();

      expect(await h.dataSource.restoreSession(), isNull);
      expect(h.appAuth.tokenRequests, isEmpty);
    });

    test('refreshes the stored session and resolves the courier', () async {
      final h = Harness(validToken: 'acc-2');
      h.storage.refreshToken = 'ref-stored';

      final session = await h.dataSource.restoreSession();

      expect(session, isNotNull);
      expect(session!.accessToken, 'acc-2');
      expect(session.courierId, 'courier-1');
      expect(h.appAuth.tokenRequests.single.refreshToken, 'ref-stored');
      expect(h.storage.refreshToken, 'ref-2');
    });

    test('a network failure keeps the stored tokens', () async {
      final h = Harness();
      h.storage.refreshToken = 'ref-ok';
      h.appAuth.onToken = (_) async => throw oauthError('network_error');

      expect(await h.dataSource.restoreSession(), isNull);
      expect(h.storage.refreshToken, 'ref-ok');
    });
  });

  group('logout', () {
    test('ends the Keycloak session with the id token hint and clears storage', () async {
      final h = Harness();
      await h.dataSource.login();

      await h.dataSource.logout();

      final request = h.appAuth.endSessionRequests.single;
      expect(request.idTokenHint, 'id-1');
      expect(request.postLogoutRedirectUrl, 'com.pedeaqui.courier:/oauthredirect');
      expect(h.storage.refreshToken, isNull);
      expect(h.storage.idToken, isNull);
      expect(h.session.accessToken, isNull);
    });

    test('clears local tokens even when end-session fails', () async {
      final h = Harness();
      await h.dataSource.login();
      h.appAuth.endSessionError = userCancelled();

      await h.dataSource.logout();

      expect(h.storage.refreshToken, isNull);
    });
  });
}
