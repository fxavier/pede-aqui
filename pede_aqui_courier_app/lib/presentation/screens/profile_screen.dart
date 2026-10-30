import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:get_it/get_it.dart';

import '../../app.dart';
import '../../core/constants/app_colors.dart';
import '../../data/repositories/auth_repository.dart';
import '../cubits/profile/profile_cubit.dart';
import '../cubits/profile/profile_state.dart';
import '../widgets/app_avatar.dart';
import '../widgets/app_card.dart';
import '../widgets/app_top_bar.dart';

class ProfileScreen extends StatelessWidget {
  const ProfileScreen({super.key});

  /// Ends the Keycloak session (end-session + local token wipe), then returns to login.
  Future<void> _logout(BuildContext context) async {
    final navigator = Navigator.of(context);
    await GetIt.instance<AuthRepository>().logout();
    navigator.pushNamedAndRemoveUntil(AppRoutes.login, (_) => false);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: const AppTopBar(title: 'Perfil'),
      body: BlocBuilder<ProfileCubit, ProfileState>(
        builder: (context, state) {
          final profile = state.profile;
          if (state.isLoading && profile == null) return const Center(child: CircularProgressIndicator());

          return ListView(
            padding: const EdgeInsets.fromLTRB(18, 18, 18, 112),
            children: [
              AppCard(
                child: Column(
                  children: [
                    const AppAvatar(size: 84),
                    const SizedBox(height: 12),
                    Text(profile?.name ?? 'Félix Mabunda', style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w900)),
                    Text(profile?.city ?? 'Maputo, Moçambique', style: const TextStyle(color: AppColors.onSurfaceVariant)),
                    const SizedBox(height: 18),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceAround,
                      children: [
                        _Metric(label: 'Avaliação', value: profile?.rating.toStringAsFixed(2) ?? '4.92'),
                        _Metric(label: 'Entregas', value: '${profile?.totalDeliveries ?? 1248}'),
                        _Metric(label: 'Hoje', value: '${profile?.completedToday ?? 6}'),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 18),
              AppCard(
                padding: EdgeInsets.zero,
                child: Column(
                  children: [
                    _ProfileAction(icon: Icons.account_balance_wallet_outlined, label: 'Carteira e levantamentos', onTap: () => Navigator.of(context).pushNamed(AppRoutes.wallet)),
                    _ProfileAction(icon: Icons.motorcycle_outlined, label: profile?.vehicle ?? 'Mota', onTap: () {}),
                    _ProfileAction(icon: Icons.notifications_none, label: 'Notificações', onTap: () => Navigator.of(context).pushNamed(AppRoutes.notifications)),
                    _ProfileAction(icon: Icons.settings_outlined, label: 'Definições', onTap: () => Navigator.of(context).pushNamed(AppRoutes.settings)),
                    _ProfileAction(icon: Icons.logout, label: 'Terminar sessão', color: AppColors.error, onTap: () => _logout(context)),
                  ],
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _Metric extends StatelessWidget {
  const _Metric({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(value, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w900, color: AppColors.primary)),
        Text(label, style: const TextStyle(color: AppColors.onSurfaceVariant, fontWeight: FontWeight.w700)),
      ],
    );
  }
}

class _ProfileAction extends StatelessWidget {
  const _ProfileAction({required this.icon, required this.label, required this.onTap, this.color = AppColors.onSurface});

  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(icon, color: color),
      title: Text(label, style: TextStyle(fontWeight: FontWeight.w800, color: color)),
      trailing: const Icon(Icons.chevron_right_rounded),
      onTap: onTap,
    );
  }
}
