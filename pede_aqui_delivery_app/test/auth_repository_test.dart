import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pede_aqui_mobile/core/network/api_client.dart';
import 'package:pede_aqui_mobile/core/storage/token_storage.dart';
import 'package:pede_aqui_mobile/features/auth/data/auth_repository.dart';

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

ResponseBody jsonResponse(Map<String, dynamic> body, {int status = 200}) {
  return ResponseBody.fromString(
    jsonEncode(body),
    status,
    headers: {
      Headers.contentTypeHeader: [Headers.jsonContentType],
    },
  );
}

Dio testDio(FakeHttpAdapter adapter, {String? baseUrl}) {
  final dio = Dio(BaseOptions(baseUrl: baseUrl ?? ''));
  dio.httpClientAdapter = adapter;
  return dio;
}

const meProfile = {
  'id': 'profile-id-1',
  'keycloakUserId': 'kc-user-1',
  'displayName': 'Felix Tester',
  'email': 'felix@example.com',
};

void main() {
  group('ApiAuthRepository token persistence', () {
    test('login stores access and refresh tokens in secure storage', () async {
      final storage = InMemoryTokenStorage();
      final tokenAdapter = FakeHttpAdapter((options) => jsonResponse({
            'access_token': 'acc-1',
            'refresh_token': 'ref-1',
          }));
      final apiAdapter = FakeHttpAdapter((options) => jsonResponse(meProfile));
      final repo = ApiAuthRepository(
        ApiClient(dio: testDio(apiAdapter, baseUrl: 'http://test.local/api/v1')),
        storage,
        authDio: testDio(tokenAdapter),
      );

      final user = await repo.login(email: 'felix@example.com', password: 'secret123');

      expect(storage.accessToken, 'acc-1');
      expect(storage.refreshToken, 'ref-1');
      expect(user.token, 'acc-1');
      expect(user.name, 'Felix Tester');
      expect(repo.customerId, 'kc-user-1');
    });

    test('logout clears persisted tokens and customer id', () async {
      final storage = InMemoryTokenStorage()
        ..accessToken = 'acc-1'
        ..refreshToken = 'ref-1';
      final repo = ApiAuthRepository(
        ApiClient(dio: testDio(FakeHttpAdapter((_) => jsonResponse({})))),
        storage,
        authDio: testDio(FakeHttpAdapter((_) => jsonResponse({}))),
      );

      await repo.logout();

      expect(storage.accessToken, isNull);
      expect(storage.refreshToken, isNull);
      expect(() => repo.customerId, throwsStateError);
    });

    test('restoreSession returns null when nothing is persisted', () async {
      final repo = ApiAuthRepository(
        ApiClient(dio: testDio(FakeHttpAdapter((_) => jsonResponse({})))),
        InMemoryTokenStorage(),
        authDio: testDio(FakeHttpAdapter((_) => jsonResponse({}))),
      );

      expect(await repo.restoreSession(), isNull);
    });

    test('restoreSession restores the user from a stored token', () async {
      final storage = InMemoryTokenStorage()
        ..accessToken = 'acc-restored'
        ..refreshToken = 'ref-restored';
      final apiAdapter = FakeHttpAdapter((options) => jsonResponse(meProfile));
      final repo = ApiAuthRepository(
        ApiClient(dio: testDio(apiAdapter, baseUrl: 'http://test.local/api/v1')),
        storage,
        authDio: testDio(FakeHttpAdapter((_) => jsonResponse({}))),
      );

      final user = await repo.restoreSession();

      expect(user, isNotNull);
      expect(user!.token, 'acc-restored');
      expect(repo.customerId, 'kc-user-1');
      // The restored token was sent as a bearer header on /me.
      expect(apiAdapter.requests.single.headers['Authorization'], 'Bearer acc-restored');
    });

    test('restoreSession clears storage when the stored token is rejected', () async {
      final storage = InMemoryTokenStorage()..accessToken = 'acc-expired';
      final apiAdapter = FakeHttpAdapter((options) => jsonResponse({}, status: 401));
      final repo = ApiAuthRepository(
        ApiClient(dio: testDio(apiAdapter, baseUrl: 'http://test.local/api/v1')),
        storage,
        authDio: testDio(FakeHttpAdapter((_) => jsonResponse({}, status: 401))),
      );

      final user = await repo.restoreSession();

      expect(user, isNull);
      expect(storage.accessToken, isNull);
    });
  });

  group('registration request mapping', () {
    test('splits a full name into firstName and lastName', () {
      final payload = ApiAuthRepository.buildRegistrationPayload(
        name: 'Ana Maria dos Santos',
        email: 'ana@example.com',
        password: 'password123',
      );

      expect(payload, {
        'firstName': 'Ana',
        'lastName': 'Maria dos Santos',
        'email': 'ana@example.com',
        'password': 'password123',
      });
    });

    test('reuses a single name for lastName (backend requires both)', () {
      final payload = ApiAuthRepository.buildRegistrationPayload(
        name: '  Felix ',
        email: 'felix@example.com',
        password: 'password123',
      );

      expect(payload['firstName'], 'Felix');
      expect(payload['lastName'], 'Felix');
    });

    test('register posts to /customers/register and then logs in', () async {
      final storage = InMemoryTokenStorage();
      final tokenAdapter = FakeHttpAdapter((options) => jsonResponse({
            'access_token': 'acc-new',
            'refresh_token': 'ref-new',
          }));
      final apiAdapter = FakeHttpAdapter((options) {
        if (options.path.contains('/customers/register')) {
          return jsonResponse({
            'keycloakUserId': 'kc-user-1',
            'email': 'felix@example.com',
            'displayName': 'Felix Tester',
          }, status: 201);
        }
        return jsonResponse(meProfile);
      });
      final repo = ApiAuthRepository(
        ApiClient(dio: testDio(apiAdapter, baseUrl: 'http://test.local/api/v1')),
        storage,
        authDio: testDio(tokenAdapter),
      );

      final user = await repo.register(
        name: 'Felix Tester',
        email: 'felix@example.com',
        password: 'password123',
      );

      final registerRequest =
          apiAdapter.requests.firstWhere((r) => r.path.contains('/customers/register'));
      expect(registerRequest.method, 'POST');
      expect(registerRequest.data, {
        'firstName': 'Felix',
        'lastName': 'Tester',
        'email': 'felix@example.com',
        'password': 'password123',
      });
      // Registration returns no tokens, so a login must follow.
      expect(tokenAdapter.requests, hasLength(1));
      expect(storage.accessToken, 'acc-new');
      expect(user.token, 'acc-new');
    });
  });
}
