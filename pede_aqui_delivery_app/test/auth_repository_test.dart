import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_appauth/flutter_appauth.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pede_aqui_mobile/core/auth/oidc_session.dart';
import 'package:pede_aqui_mobile/core/network/api_client.dart';
import 'package:pede_aqui_mobile/features/auth/data/auth_repository.dart';
import 'package:pede_aqui_mobile/features/auth/data/password_policy.dart';

import 'support/auth_fakes.dart';

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

ResponseBody jsonResponse(Map<String, dynamic> body, {int status = 200}) {
  return ResponseBody.fromString(
    jsonEncode(body),
    status,
    headers: {
      Headers.contentTypeHeader: [Headers.jsonContentType],
    },
  );
}

const meProfile = {
  'id': 'profile-id-1',
  'keycloakUserId': 'kc-user-1',
  'displayName': 'Felix Tester',
  'email': 'felix@example.com',
};

/// Answers /me with the profile, but only for the [validToken] bearer.
ResponseBody Function(RequestOptions) profileFor(String validToken) => (options) {
      if (options.headers['Authorization'] != 'Bearer $validToken') {
        return jsonResponse({'code': 'unauthorized'}, status: 401);
      }
      return jsonResponse(meProfile);
    };

class Harness {
  Harness({ResponseBody Function(RequestOptions)? api})
      : adapter = FakeHttpAdapter(api ?? (_) => jsonResponse(meProfile)) {
    apiClient = ApiClient(
      dio: Dio(BaseOptions(baseUrl: 'http://test.local/api/v1'))..httpClientAdapter = adapter,
    );
    session = OidcSession(config: testOidcConfig, storage: storage, appAuth: appAuth);
    repo = ApiAuthRepository(apiClient, session);
    apiClient.onRefreshToken = repo.refreshToken;
  }

  final FakeHttpAdapter adapter;
  final storage = InMemoryTokenStorage();
  final appAuth = FakeAppAuth();
  late final ApiClient apiClient;
  late final OidcSession session;
  late final ApiAuthRepository repo;
}

void main() {
  group('login (Authorization Code + PKCE)', () {
    test('maps the PKCE tokens: refresh/id token persisted, access token in memory', () async {
      final h = Harness();

      final user = await h.repo.login();

      final request = h.appAuth.authorizeRequests.single;
      expect(request.clientId, 'pede-aqui-mobile');
      expect(request.redirectUrl, 'com.pedeaqui.delivery:/oauthredirect');
      expect(request.issuer, 'http://keycloak.test/realms/delivery');
      expect(request.scopes, containsAll(['openid', 'profile', 'email']));
      expect(request.promptValues, [Prompt.login]);
      expect(request.allowInsecureConnections, isTrue); // http issuer (local dev)
      expect(h.storage.refreshToken, 'ref-1');
      expect(h.storage.idToken, 'id-1');
      expect(h.session.accessToken, 'acc-1');
      expect(user.token, 'acc-1');
      expect(user.name, 'Felix Tester');
      expect(h.repo.customerId, 'kc-user-1');
      expect(h.adapter.requests.single.headers['Authorization'], 'Bearer acc-1');
    });

    test('no password grant: nothing is posted to a token endpoint by the app', () async {
      final h = Harness();

      await h.repo.login();

      expect(h.adapter.requests.map((r) => r.path), ['/me']);
      expect(h.appAuth.tokenRequests, isEmpty);
    });

    test('closing the browser surfaces AuthCancelledException and stores nothing', () async {
      final h = Harness();
      h.appAuth.onAuthorize = (_) async => throw userCancelled();

      await expectLater(h.repo.login(), throwsA(isA<AuthCancelledException>()));
      expect(h.storage.refreshToken, isNull);
      expect(h.adapter.requests, isEmpty);
    });

    test('a profile failure after sign-in drops the half-open session', () async {
      final h = Harness(api: (_) => jsonResponse({'code': 'boom'}, status: 500));

      await expectLater(h.repo.login(), throwsA(isA<DioException>()));
      expect(h.storage.refreshToken, isNull);
      expect(h.session.accessToken, isNull);
    });
  });

  group('refresh with rotation', () {
    test('concurrent 401s trigger a single refresh and store the rotated token', () async {
      final h = Harness(api: profileFor('acc-2'));
      h.storage.refreshToken = 'ref-1';
      h.apiClient.setAuthToken('acc-expired');
      final gate = Completer<void>();
      h.appAuth.onToken = (_) async {
        await gate.future; // keep the refresh in flight while the other 401s arrive
        return tokenResponse(access: 'acc-2', refresh: 'ref-2', id: 'id-2');
      };

      final pending = [for (var i = 0; i < 3; i++) h.apiClient.get<Map<String, dynamic>>('/me')];
      await Future<void>.delayed(const Duration(milliseconds: 20));
      gate.complete();
      final responses = await Future.wait(pending);

      expect(responses.map((r) => r.statusCode), everyElement(200));
      expect(h.appAuth.tokenRequests, hasLength(1));
      final refresh = h.appAuth.tokenRequests.single;
      expect(refresh.refreshToken, 'ref-1');
      expect(refresh.grantType, GrantType.refreshToken);
      expect(refresh.clientId, 'pede-aqui-mobile');
      expect(h.storage.refreshToken, 'ref-2');
      expect(h.storage.idToken, 'id-2');
    });

    test('direct concurrent refresh calls share one token request', () async {
      final h = Harness();
      h.storage.refreshToken = 'ref-1';

      await Future.wait([for (var i = 0; i < 3; i++) h.repo.refreshToken()]);

      expect(h.appAuth.tokenRequests, hasLength(1));
    });

    test('the next refresh uses the rotated refresh token', () async {
      final h = Harness();
      h.storage.refreshToken = 'ref-1';
      var n = 1;
      h.appAuth.onToken = (_) async {
        n++;
        return tokenResponse(access: 'acc-$n', refresh: 'ref-$n');
      };

      await h.repo.refreshToken();
      await h.repo.refreshToken();

      expect(h.appAuth.tokenRequests.map((r) => r.refreshToken), ['ref-1', 'ref-2']);
      expect(h.storage.refreshToken, 'ref-3');
    });

    test('invalid_grant expires the session and wipes storage', () async {
      final h = Harness();
      h.storage
        ..refreshToken = 'ref-dead'
        ..idToken = 'id-1';
      h.appAuth.onToken = (_) async => throw oauthError('invalid_grant');

      await expectLater(h.repo.refreshToken(), throwsA(isA<SessionExpiredException>()));
      expect(h.storage.refreshToken, isNull);
      expect(h.storage.idToken, isNull);
    });
  });

  group('restoreSession', () {
    test('returns null without contacting Keycloak when nothing is persisted', () async {
      final h = Harness();

      expect(await h.repo.restoreSession(), isNull);
      expect(h.appAuth.tokenRequests, isEmpty);
    });

    test('refreshes the stored session and loads the profile', () async {
      final h = Harness(api: profileFor('acc-2'));
      h.storage.refreshToken = 'ref-stored';

      final user = await h.repo.restoreSession();

      expect(user, isNotNull);
      expect(user!.token, 'acc-2');
      expect(h.appAuth.tokenRequests.single.refreshToken, 'ref-stored');
      expect(h.storage.refreshToken, 'ref-2');
      expect(h.repo.customerId, 'kc-user-1');
    });

    test('a rejected refresh token clears storage', () async {
      final h = Harness();
      h.storage.refreshToken = 'ref-dead';
      h.appAuth.onToken = (_) async => throw oauthError('invalid_grant');

      expect(await h.repo.restoreSession(), isNull);
      expect(h.storage.refreshToken, isNull);
    });

    test('a network failure keeps the stored tokens for the next start', () async {
      final h = Harness();
      h.storage.refreshToken = 'ref-ok';
      h.appAuth.onToken = (_) async => throw oauthError('network_error');

      expect(await h.repo.restoreSession(), isNull);
      expect(h.storage.refreshToken, 'ref-ok');
    });
  });

  group('logout', () {
    test('ends the Keycloak session with the id token hint and clears storage', () async {
      final h = Harness();
      await h.repo.login();

      await h.repo.logout();

      final request = h.appAuth.endSessionRequests.single;
      expect(request.idTokenHint, 'id-1');
      expect(request.postLogoutRedirectUrl, 'com.pedeaqui.delivery:/oauthredirect');
      expect(request.issuer, 'http://keycloak.test/realms/delivery');
      expect(h.storage.refreshToken, isNull);
      expect(h.storage.idToken, isNull);
      expect(h.session.accessToken, isNull);
      expect(() => h.repo.customerId, throwsStateError);
    });

    test('clears local tokens even when end-session fails', () async {
      final h = Harness();
      await h.repo.login();
      h.appAuth.endSessionError = oauthError('network_error');

      await h.repo.logout();

      expect(h.storage.refreshToken, isNull);
    });
  });

  group('registration', () {
    test('splits a full name into firstName and lastName', () {
      final payload = ApiAuthRepository.buildRegistrationPayload(
        name: 'Ana Maria dos Santos',
        email: 'ana@example.com',
        password: 'Password123',
      );

      expect(payload, {
        'firstName': 'Ana',
        'lastName': 'Maria dos Santos',
        'email': 'ana@example.com',
        'password': 'Password123',
      });
    });

    test('reuses a single name for lastName (backend requires both)', () {
      final payload = ApiAuthRepository.buildRegistrationPayload(
        name: '  Felix ',
        email: 'felix@example.com',
        password: 'Password123',
      );

      expect(payload['firstName'], 'Felix');
      expect(payload['lastName'], 'Felix');
    });

    test('posts to /customers/register, then starts PKCE with login_hint', () async {
      final h = Harness(api: (options) {
        if (options.path.contains('/customers/register')) {
          return jsonResponse({'keycloakUserId': 'kc-user-1'}, status: 201);
        }
        return jsonResponse(meProfile);
      },);

      final user = await h.repo.register(
        name: 'Felix Tester',
        email: 'felix@example.com',
        password: 'Password123',
      );

      final registerRequest =
          h.adapter.requests.firstWhere((r) => r.path.contains('/customers/register'));
      expect(registerRequest.method, 'POST');
      expect(registerRequest.data, {
        'firstName': 'Felix',
        'lastName': 'Tester',
        'email': 'felix@example.com',
        'password': 'Password123',
      });
      expect(h.appAuth.authorizeRequests.single.loginHint, 'felix@example.com');
      expect(user.token, 'acc-1');
      expect(h.storage.refreshToken, 'ref-1');
    });

    test('a password policy rejection becomes a readable RegistrationException', () async {
      final h = Harness(
        api: (_) => jsonResponse({
          'code': 'validation_failed',
          'fieldErrors': [
            {'field': 'password', 'message': 'must contain at least one digit'},
          ],
        }, status: 400,),
      );

      await expectLater(
        h.repo.register(name: 'Felix', email: 'felix@example.com', password: 'Passwordxx'),
        throwsA(
          isA<RegistrationException>().having(
            (e) => e.message,
            'message',
            'A palavra-passe não cumpre a política de segurança.',
          ),
        ),
      );
      expect(h.appAuth.authorizeRequests, isEmpty);
    });
  });

  group('passwordPolicyError (mirrors the realm policy)', () {
    test('accepts a compliant password', () {
      expect(passwordPolicyError('Password123', email: 'a@b.co'), isNull);
    });

    test('rejects each unmet rule', () {
      expect(passwordPolicyError('Pass1', email: 'a@b.co'), contains('10 caracteres'));
      expect(passwordPolicyError('Passwordxyz', email: 'a@b.co'), contains('algarismo'));
      expect(passwordPolicyError('password123', email: 'a@b.co'), contains('maiúscula'));
      expect(passwordPolicyError('PASSWORD123', email: 'a@b.co'), contains('minúscula'));
      expect(passwordPolicyError('Felix1@Ex.com', email: 'felix1@ex.com'), contains('email'));
    });
  });
}
