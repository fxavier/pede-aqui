import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../app/pede_aqui_app.dart';
import '../../../core/constants/app_colors.dart';
import '../../../shared/widgets/app_logo.dart';
import '../../../shared/widgets/primary_button.dart';
import '../../../shared/widgets/screen_shell.dart';
import 'auth_cubit.dart';

class LoginRegisterScreen extends StatefulWidget {
  const LoginRegisterScreen({super.key});

  @override
  State<LoginRegisterScreen> createState() => _LoginRegisterScreenState();
}

class _LoginRegisterScreenState extends State<LoginRegisterScreen> {
  bool _register = false;
  final _nameController = TextEditingController();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  String? _validationError;

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return BlocListener<AuthCubit, AuthState>(
      listenWhen: (previous, current) =>
          !previous.authenticated && current.authenticated,
      listener: (context, state) => Navigator.pushNamedAndRemoveUntil(
          context, AppRoutes.home, (route) => false),
      child: ScreenShell(
        padding: const EdgeInsets.fromLTRB(24, 34, 24, 32),
        child: Column(
          children: [
            const SizedBox(height: 28),
            const AppLogo(),
            const SizedBox(height: 42),
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(28),
                border: Border.all(color: AppColors.border),
                boxShadow: [
                  BoxShadow(
                      color: Colors.black.withValues(alpha: .07),
                      blurRadius: 34,
                      offset: const Offset(0, 18)),
                ],
              ),
              child: Column(
                children: [
                  Row(
                    children: [
                      _TabButton(
                          label: 'Entrar',
                          selected: !_register,
                          onTap: () => setState(() => _register = false)),
                      _TabButton(
                          label: 'Registar',
                          selected: _register,
                          onTap: () => setState(() => _register = true)),
                    ],
                  ),
                  const SizedBox(height: 24),
                  if (_register) ...[
                    TextField(
                      controller: _nameController,
                      decoration:
                          const InputDecoration(labelText: 'Nome completo'),
                    ),
                    const SizedBox(height: 14),
                  ],
                  TextField(
                    controller: _emailController,
                    keyboardType: TextInputType.emailAddress,
                    decoration: const InputDecoration(labelText: 'Email'),
                  ),
                  const SizedBox(height: 14),
                  TextField(
                    controller: _passwordController,
                    obscureText: true,
                    decoration:
                        const InputDecoration(labelText: 'Palavra-passe'),
                  ),
                  const SizedBox(height: 10),
                  Align(
                    alignment: Alignment.centerRight,
                    child: TextButton(
                      onPressed: () {},
                      child: const Text('Esqueceu a palavra-passe?',
                          style: TextStyle(
                              color: AppColors.primary,
                              fontWeight: FontWeight.w800)),
                    ),
                  ),
                  const SizedBox(height: 12),
                  if (_validationError != null) ...[
                    Text(
                      _validationError!,
                      style: const TextStyle(color: Colors.red, fontSize: 14),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 8),
                  ],
                  BlocBuilder<AuthCubit, AuthState>(
                    builder: (context, state) {
                      if (state.error != null) {
                        return Column(
                          children: [
                            Text(
                              state.error!,
                              style: const TextStyle(
                                  color: Colors.red, fontSize: 14),
                              textAlign: TextAlign.center,
                            ),
                            const SizedBox(height: 8),
                          ],
                        );
                      }
                      return const SizedBox.shrink();
                    },
                  ),
                  BlocBuilder<AuthCubit, AuthState>(
                    builder: (context, state) {
                      return PrimaryButton(
                        label: _register ? 'CRIAR CONTA' : 'ENTRAR',
                        loading: state.loading,
                        onPressed: _validateAndSubmit,
                      );
                    },
                  ),
                  const SizedBox(height: 24),
                  Row(
                    children: const [
                      Expanded(child: Divider()),
                      Padding(
                        padding: EdgeInsets.symmetric(horizontal: 12),
                        child: Text('ou continue com',
                            style: TextStyle(
                                color: AppColors.mutedText, fontSize: 12)),
                      ),
                      Expanded(child: Divider()),
                    ],
                  ),
                  const SizedBox(height: 20),
                  Tooltip(
                    message: 'Brevemente disponível',
                    child: OutlinedButton.icon(
                      onPressed: null,
                      style: OutlinedButton.styleFrom(
                        minimumSize: const Size.fromHeight(54),
                        foregroundColor: AppColors.green,
                        side: const BorderSide(color: AppColors.green),
                        shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(16)),
                      ),
                      icon: const Icon(Icons.g_mobiledata_rounded, size: 28),
                      label: const Text('Continuar com Google',
                          style: TextStyle(fontWeight: FontWeight.w800)),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),
            const Text.rich(
              TextSpan(
                text: 'Ao continuar, concorda com os nossos\n',
                children: [
                  TextSpan(
                      text: 'Termos de Serviço',
                      style: TextStyle(
                          color: AppColors.green, fontWeight: FontWeight.w800)),
                  TextSpan(text: ' e '),
                  TextSpan(
                      text: 'Política de Privacidade.',
                      style: TextStyle(
                          color: AppColors.green, fontWeight: FontWeight.w800)),
                ],
              ),
              textAlign: TextAlign.center,
              style: TextStyle(color: AppColors.mutedText, height: 1.5),
            ),
          ],
        ),
      ),
    );
  }

  void _validateAndSubmit() {
    setState(() => _validationError = null);

    final email = _emailController.text.trim();
    final password = _passwordController.text;
    final name = _nameController.text.trim();

    if (_register && name.isEmpty) {
      setState(() => _validationError = 'O nome é obrigatório.');
      return;
    }

    if (email.isEmpty || !email.contains('@')) {
      setState(() => _validationError = 'Email inválido.');
      return;
    }

    if (password.length < 6) {
      setState(() => _validationError =
          'A palavra-passe deve ter pelo menos 6 caracteres.');
      return;
    }

    final cubit = context.read<AuthCubit>();
    if (_register) {
      cubit.register(name, email, password);
    } else {
      cubit.login(email, password);
    }
  }
}

class _TabButton extends StatelessWidget {
  const _TabButton(
      {required this.label, required this.selected, required this.onTap});

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: InkWell(
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          padding: const EdgeInsets.only(bottom: 14),
          decoration: BoxDecoration(
            border: Border(
                bottom: BorderSide(
                    color: selected ? AppColors.primary : AppColors.border,
                    width: 2)),
          ),
          child: Text(
            label,
            textAlign: TextAlign.center,
            style: TextStyle(
                fontWeight: FontWeight.w900,
                color: selected ? AppColors.primary : AppColors.mutedText),
          ),
        ),
      ),
    );
  }
}
