import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../core/auth/oidc_session.dart';
import '../data/auth_repository.dart';

class AuthState extends Equatable {
  const AuthState({
    this.loading = false,
    this.user,
    this.error,
  });

  final bool loading;
  final AuthUser? user;
  final String? error;

  bool get authenticated => user != null;

  AuthState copyWith({bool? loading, AuthUser? user, String? error, bool clearUser = false}) {
    return AuthState(
      loading: loading ?? this.loading,
      user: clearUser ? null : user ?? this.user,
      error: error,
    );
  }

  @override
  List<Object?> get props => [loading, user, error];
}

class AuthCubit extends Cubit<AuthState> {
  AuthCubit(this._repository) : super(const AuthState());

  final AuthRepository _repository;

  /// Opens the Keycloak login page (PKCE). Closing the browser is not an error.
  Future<void> login() async {
    emit(state.copyWith(loading: true));
    try {
      final user = await _repository.login();
      emit(state.copyWith(loading: false, user: user));
    } on AuthCancelledException {
      emit(state.copyWith(loading: false));
    } catch (_) {
      emit(state.copyWith(loading: false, error: 'Não foi possível iniciar sessão. Tente novamente.'));
    }
  }

  /// Creates the account on the backend, then continues to the Keycloak login.
  Future<void> register(String name, String email, String password) async {
    emit(state.copyWith(loading: true));
    try {
      final user = await _repository.register(name: name, email: email, password: password);
      emit(state.copyWith(loading: false, user: user));
    } on AuthCancelledException {
      // Account exists now; the user just did not finish signing in.
      emit(state.copyWith(loading: false, error: 'Conta criada. Entre para continuar.'));
    } on RegistrationException catch (e) {
      emit(state.copyWith(loading: false, error: e.message));
    } catch (_) {
      emit(state.copyWith(loading: false, error: 'Não foi possível criar conta.'));
    }
  }

  /// Attempts auto-login from a persisted session at app startup.
  Future<void> restoreSession() async {
    try {
      final user = await _repository.restoreSession();
      if (user != null) {
        emit(state.copyWith(user: user));
      }
    } catch (_) {
      // No persisted session (or it expired): stay unauthenticated silently.
    }
  }

  Future<void> logout() async {
    await _repository.logout();
    emit(state.copyWith(clearUser: true));
  }
}
