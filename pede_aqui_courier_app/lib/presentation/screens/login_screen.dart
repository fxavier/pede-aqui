import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:get_it/get_it.dart';

import '../../app.dart';
import '../../core/constants/app_colors.dart';
import '../cubits/auth/auth_cubit.dart';
import '../cubits/auth/auth_state.dart';

/// Courier sign-in: hands off to the Keycloak login page (Authorization Code +
/// PKCE). The app never sees the courier's password.
class LoginScreen extends StatelessWidget {
  const LoginScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocProvider<AuthCubit>(
      create: (_) => GetIt.instance<AuthCubit>()..restoreSession(),
      child: BlocConsumer<AuthCubit, AuthState>(
        listener: (context, state) {
          if (state is AuthAuthenticated) {
            Navigator.of(context).pushReplacementNamed(AppRoutes.shell);
          }
        },
        builder: (context, state) {
          return Scaffold(
            body: SafeArea(
              child: ListView(
                padding: const EdgeInsets.all(24),
                children: [
                  const SizedBox(height: 40),
                  const Icon(Icons.delivery_dining_rounded, color: AppColors.primary, size: 84),
                  const SizedBox(height: 18),
                  const Text('Pede Aqui Estafeta', textAlign: TextAlign.center, style: TextStyle(fontSize: 32, fontWeight: FontWeight.w900, color: AppColors.primary)),
                  const SizedBox(height: 8),
                  const Text('Entre para gerir entregas em Maputo, acompanhar ganhos e confirmar pedidos.', textAlign: TextAlign.center, style: TextStyle(color: AppColors.onSurfaceVariant, fontSize: 16, height: 1.4)),
                  const SizedBox(height: 34),
                  const Text(
                    'Vai abrir a página segura de autenticação Pede Aqui. A sua palavra-passe é introduzida apenas lá.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: AppColors.onSurfaceVariant, height: 1.4),
                  ),
                  const SizedBox(height: 18),
                  if (state is AuthError) ...[
                    Text(
                      state.message,
                      style: const TextStyle(color: AppColors.error, fontSize: 14),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 8),
                  ],
                  const SizedBox(height: 10),
                  FilledButton.icon(
                    onPressed: state is AuthLoading ? null : () => context.read<AuthCubit>().login(),
                    icon: state is AuthLoading
                        ? const SizedBox(
                            height: 20,
                            width: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.lock_outline_rounded),
                    label: const Text('Entrar com Pede Aqui'),
                  ),
                  const SizedBox(height: 16),
                  // Couriers are onboarded by the Pede Aqui team; there is no self-registration.
                  const TextButton(
                    onPressed: null,
                    child: Text('Criar conta de estafeta'),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}
