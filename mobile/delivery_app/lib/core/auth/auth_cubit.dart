import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:pede_aqui_delivery_app/core/auth/keycloak_auth_service.dart';

class AuthState {
  const AuthState({
    required this.authenticated,
    this.token,
    this.loading = false,
    this.error,
  });

  final bool authenticated;
  final String? token;
  final bool loading;
  final String? error;
}

class AuthCubit extends Cubit<AuthState> {
  AuthCubit(this.authService) : super(const AuthState(authenticated: false));

  final KeycloakAuthService authService;

  Future<void> loginWithKeycloak() async {
    emit(const AuthState(authenticated: false, loading: true));
    try {
      final token = await authService.loginWithPkce();
      emit(AuthState(authenticated: true, token: token));
    } catch (error) {
      emit(AuthState(authenticated: false, error: error.toString()));
    }
  }

  void loginWithToken(String token) {
    emit(AuthState(authenticated: true, token: token));
  }

  void logout() {
    emit(const AuthState(authenticated: false));
  }
}
