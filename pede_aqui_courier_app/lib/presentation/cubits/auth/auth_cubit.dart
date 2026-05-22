import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../data/repositories/auth_repository.dart';
import 'auth_state.dart';

class AuthCubit extends Cubit<AuthState> {
  AuthCubit(this._repository) : super(const AuthInitial());

  final AuthRepository _repository;

  Future<void> login({required String phone, required String password}) async {
    emit(const AuthLoading());
    try {
      final result = await _repository.login(phone: phone, password: password);
      emit(AuthAuthenticated(
        courierId: result.courierId,
        accessToken: result.accessToken,
      ));
    } on Exception catch (e) {
      emit(AuthError(message: _mapError(e)));
    }
  }

  Future<void> logout() async {
    await _repository.logout();
    emit(const AuthInitial());
  }

  String _mapError(Exception e) {
    final msg = e.toString().toLowerCase();
    if (msg.contains('401') || msg.contains('invalid') || msg.contains('unauthorized')) {
      return 'Credenciais inválidas. Verifique o telefone e a palavra-passe.';
    }
    if (msg.contains('network') || msg.contains('connection') || msg.contains('socket')) {
      return 'Sem ligação à internet. Verifique a sua rede.';
    }
    return 'Não foi possível iniciar sessão. Tente novamente.';
  }
}