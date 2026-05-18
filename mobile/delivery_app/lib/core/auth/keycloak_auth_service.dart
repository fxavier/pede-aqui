import 'package:flutter_appauth/flutter_appauth.dart';
import 'package:pede_aqui_delivery_app/core/config/app_config.dart';

class KeycloakAuthService {
  KeycloakAuthService({FlutterAppAuth? appAuth}) : _appAuth = appAuth ?? const FlutterAppAuth();

  final FlutterAppAuth _appAuth;

  Future<String> loginWithPkce() async {
    final result = await _appAuth.authorizeAndExchangeCode(
      AuthorizationTokenRequest(
        AppConfig.keycloakClientId,
        AppConfig.keycloakRedirectUri,
        issuer: AppConfig.keycloakIssuer,
        scopes: const ['openid', 'profile', 'email'],
        promptValues: const ['login'],
      ),
    );

    final token = result?.accessToken;
    if (token == null || token.isEmpty) {
      throw Exception('Falha ao autenticar com Keycloak.');
    }
    return token;
  }
}
