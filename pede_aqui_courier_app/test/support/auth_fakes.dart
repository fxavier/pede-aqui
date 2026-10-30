import 'package:flutter_appauth/flutter_appauth.dart';
import 'package:pede_aqui_courier_app/core/auth/oidc_session.dart';
import 'package:pede_aqui_courier_app/core/storage/token_storage.dart';

const testOidcConfig = OidcConfig(
  issuer: 'http://keycloak.test/realms/delivery',
  clientId: 'pede-aqui-courier-mobile',
  redirectUri: 'com.pedeaqui.courier:/oauthredirect',
);

/// In-memory TokenStorage fake so tests avoid platform channels.
class InMemoryTokenStorage implements TokenStorage {
  String? refreshToken;
  String? idToken;

  @override
  Future<void> saveSession({required String refreshToken, String? idToken}) async {
    this.refreshToken = refreshToken;
    if (idToken != null) this.idToken = idToken;
  }

  @override
  Future<String?> readRefreshToken() async => refreshToken;

  @override
  Future<String?> readIdToken() async => idToken;

  @override
  Future<void> clear() async {
    refreshToken = null;
    idToken = null;
  }
}

/// Stands in for the AppAuth platform plugin and records every request.
class FakeAppAuth implements FlutterAppAuth {
  final List<AuthorizationTokenRequest> authorizeRequests = [];
  final List<TokenRequest> tokenRequests = [];
  final List<EndSessionRequest> endSessionRequests = [];

  Future<AuthorizationTokenResponse> Function(AuthorizationTokenRequest request) onAuthorize =
      (_) async => authorizationResponse(access: 'acc-1', refresh: 'ref-1', id: 'id-1');
  Future<TokenResponse> Function(TokenRequest request) onToken =
      (_) async => tokenResponse(access: 'acc-2', refresh: 'ref-2', id: 'id-2');
  Object? endSessionError;

  @override
  Future<AuthorizationTokenResponse> authorizeAndExchangeCode(AuthorizationTokenRequest request) {
    authorizeRequests.add(request);
    return onAuthorize(request);
  }

  @override
  Future<TokenResponse> token(TokenRequest request) {
    tokenRequests.add(request);
    return onToken(request);
  }

  @override
  Future<EndSessionResponse> endSession(EndSessionRequest request) async {
    endSessionRequests.add(request);
    final error = endSessionError;
    if (error != null) throw error;
    return EndSessionResponse(null);
  }

  @override
  Future<AuthorizationResponse> authorize(AuthorizationRequest request) =>
      throw UnimplementedError('only authorizeAndExchangeCode is used');
}

AuthorizationTokenResponse authorizationResponse({required String access, String? refresh, String? id}) =>
    AuthorizationTokenResponse(access, refresh, null, id, 'Bearer', null, null, null);

TokenResponse tokenResponse({required String access, String? refresh, String? id}) =>
    TokenResponse(access, refresh, null, id, 'Bearer', null, null);

FlutterAppAuthUserCancelledException userCancelled() => FlutterAppAuthUserCancelledException(
      code: 'authorize_and_exchange_code_failed',
      platformErrorDetails: FlutterAppAuthPlatformErrorDetails(error: 'user_cancelled'),
    );

FlutterAppAuthPlatformException oauthError(String error) => FlutterAppAuthPlatformException(
      code: 'token_failed',
      platformErrorDetails: FlutterAppAuthPlatformErrorDetails(error: error),
    );
