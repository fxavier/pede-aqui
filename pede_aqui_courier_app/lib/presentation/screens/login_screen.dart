import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:get_it/get_it.dart';

import '../../app.dart';
import '../../core/constants/app_colors.dart';
import '../cubits/auth/auth_cubit.dart';
import '../cubits/auth/auth_state.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _phoneController = TextEditingController();
  final _passwordController = TextEditingController();
  String? _validationError;

  @override
  void dispose() {
    _phoneController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

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
                  TextField(
                    controller: _phoneController,
                    keyboardType: TextInputType.phone,
                    decoration: const InputDecoration(
                      labelText: 'Telefone',
                      prefixIcon: Icon(Icons.phone_outlined),
                      hintText: '+258 84 123 4567',
                    ),
                  ),
                  const SizedBox(height: 14),
                  TextField(
                    controller: _passwordController,
                    obscureText: true,
                    decoration: const InputDecoration(
                      labelText: 'Palavra-passe',
                      prefixIcon: Icon(Icons.lock_outline),
                    ),
                  ),
                  const SizedBox(height: 14),
                  if (_validationError != null) ...[
                    Text(
                      _validationError!,
                      style: const TextStyle(color: Colors.red, fontSize: 14),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 8),
                  ],
                  if (state is AuthError) ...[
                    Text(
                      state.message,
                      style: const TextStyle(color: Colors.red, fontSize: 14),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 8),
                  ],
                  const SizedBox(height: 10),
                  FilledButton(
                    onPressed: state is AuthLoading ? null : _validateAndSubmit,
                    child: state is AuthLoading
                        ? const SizedBox(
                            height: 20,
                            width: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Text('Entrar'),
                  ),
                  const SizedBox(height: 16),
                  TextButton(
                    onPressed: null,
                    child: const Text('Criar conta de estafeta'),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  void _validateAndSubmit() {
    setState(() => _validationError = null);

    final phone = _phoneController.text.trim();
    final password = _passwordController.text;

    if (!phone.startsWith('+258') || phone.length < 12) {
      setState(() => _validationError = 'Número de telefone inválido. Use o formato +258 84 123 4567.');
      return;
    }

    if (password.isEmpty) {
      setState(() => _validationError = 'A palavra-passe é obrigatória.');
      return;
    }

    context.read<AuthCubit>().login(phone: phone, password: password);
  }
}
