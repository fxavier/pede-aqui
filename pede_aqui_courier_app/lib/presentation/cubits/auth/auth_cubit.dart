import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../core/auth/oidc_session.dart';
import '../../../data/repositories/auth_repository.dart';
import 'auth_state.dart';

class AuthCubit extends Cubit<AuthState> {
  AuthCubit(this._repository) : super(const AuthInitial());

  final AuthRepository _repository;

  /// Opens the Keycloak login page (PKCE). Closing the browser is not an error.
  Future<void> login() async {
    emit(const AuthLoading());
    try {
      final result = await _repository.login();
      emit(AuthAuthenticated(
        courierId: result.courierId,
        accessToken: result.accessToken,
      ));
    } on AuthCancelledException {
      emit(const AuthInitial());
    } on Exception catch (e) {
      emit(AuthError(message: _mapError(e)));
    }
  }

  /// Attempts auto-login from a persisted session at app startup.
  Future<void> restoreSession() async {
    try {
      final result = await _repository.restoreSession();
      if (result != null) {
        emit(AuthAuthenticated(
          courierId: result.courierId,
          accessToken: result.accessToken,
        ));
      }
    } catch (_) {
      // No persisted session (or it expired): stay unauthenticated silently.
    }
  }

  Future<void> logout() async {
    await _repository.logout();
    emit(const AuthInitial());
  }

  String _mapError(Exception e) {
    final msg = e.toString().toLowerCase();
    // Signed in to Keycloak, but GET /couriers/me refused or found no courier profile.
    if (msg.contains('403') || msg.contains('404')) {
      return 'Esta conta não tem perfil de estafeta. Contacte a equipa Pede Aqui.';
    }
    if (msg.contains('network') || msg.contains('connection') || msg.contains('socket')) {
      return 'Sem ligação à internet. Verifique a sua rede.';
    }
    return 'Não foi possível iniciar sessão. Tente novamente.';
  }
}