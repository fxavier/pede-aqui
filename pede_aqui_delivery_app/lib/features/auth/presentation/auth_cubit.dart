import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

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

  Future<void> login(String email, String password) async {
    emit(state.copyWith(loading: true));
    try {
      final user = await _repository.login(email: email, password: password);
      emit(state.copyWith(loading: false, user: user));
    } catch (_) {
      emit(state.copyWith(loading: false, error: 'Credenciais inválidas.'));
    }
  }

  Future<void> register(String name, String email, String password) async {
    emit(state.copyWith(loading: true));
    try {
      final user = await _repository.register(name: name, email: email, password: password);
      emit(state.copyWith(loading: false, user: user));
    } catch (_) {
      emit(state.copyWith(loading: false, error: 'Não foi possível criar conta.'));
    }
  }

  Future<void> logout() async {
    await _repository.logout();
    emit(state.copyWith(clearUser: true));
  }
}
