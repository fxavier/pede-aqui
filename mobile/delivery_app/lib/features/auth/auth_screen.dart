import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:pede_aqui_delivery_app/core/auth/auth_cubit.dart';

class AuthScreen extends StatelessWidget {
  const AuthScreen({super.key, required this.cubit, required this.onAuthenticated});

  final AuthCubit cubit;
  final VoidCallback onAuthenticated;

  @override
  Widget build(BuildContext context) {
    return BlocListener<AuthCubit, AuthState>(
      bloc: cubit,
      listener: (context, state) {
        if (state.authenticated) onAuthenticated();
      },
      child: Scaffold(
        appBar: AppBar(title: const Text('Entrar - Pede Aqui')),
        body: Padding(
          padding: const EdgeInsets.all(16),
          child: BlocBuilder<AuthCubit, AuthState>(
            bloc: cubit,
            builder: (context, state) => Column(
              children: [
                const Text('Entrar com Keycloak usando fluxo seguro PKCE.'),
                const SizedBox(height: 12),
                if (state.loading) const CircularProgressIndicator(),
                if (state.error != null) Text('Erro: ${state.error}', style: const TextStyle(color: Colors.red)),
                FilledButton(
                  onPressed: state.loading ? null : cubit.loginWithKeycloak,
                  child: const Text('Entrar com Keycloak'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
