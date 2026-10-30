import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../app.dart';
import '../../core/constants/app_colors.dart';
import '../cubits/dashboard/dashboard_cubit.dart';
import '../cubits/dashboard/dashboard_state.dart';
import '../cubits/earnings/earnings_cubit.dart';
import '../cubits/earnings/earnings_state.dart';
import '../cubits/location_consent/location_consent_cubit.dart';
import '../cubits/profile/profile_cubit.dart';
import '../cubits/profile/profile_state.dart';
import '../widgets/app_top_bar.dart';
import '../widgets/dashboard_cards.dart';
import '../widgets/earnings_widgets.dart';
import '../widgets/section_header.dart';

class HomeDashboardScreen extends StatelessWidget {
  const HomeDashboardScreen({super.key});

  /// Going online requires a valid GPS consent (LAC-40): without it, show the consent screen first.
  static Future<void> _toggle(BuildContext context, bool value) async {
    final dashboard = context.read<DashboardCubit>();
    if (value && !context.read<LocationConsentCubit>().state.isValid) {
      final accepted = await Navigator.of(context).pushNamed<bool>(AppRoutes.locationConsent);
      if (accepted != true) return;
    }
    await dashboard.toggleAvailability(value);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: PreferredSize(
        preferredSize: const Size.fromHeight(64),
        child: BlocBuilder<ProfileCubit, ProfileState>(
          builder: (context, profileState) {
            final firstName = profileState.profile?.name.split(' ').first ?? 'Félix';
            return AppTopBar(title: 'Olá, $firstName!', showAvatar: true);
          },
        ),
      ),
      body: BlocListener<DashboardCubit, DashboardState>(
        listenWhen: (previous, current) =>
            current.errorMessage != null && previous.errorMessage != current.errorMessage,
        listener: (context, state) =>
            ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(state.errorMessage!))),
        child: RefreshIndicator(
          color: AppColors.primary,
          onRefresh: () async {
            await Future.wait([
              context.read<DashboardCubit>().loadDashboard(),
              context.read<EarningsCubit>().loadEarnings(),
            ]);
          },
          child: ListView(
            padding: const EdgeInsets.fromLTRB(18, 18, 18, 112),
            children: [
              BlocBuilder<DashboardCubit, DashboardState>(
                builder: (context, state) {
                  if (state.isLoading && state.activeDelivery == null) {
                    return const Center(
                        child: Padding(padding: EdgeInsets.all(32), child: CircularProgressIndicator()));
                  }
                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      AvailabilityCard(
                        isAvailable: state.isAvailable,
                        onChanged: (value) => _toggle(context, value),
                      ),
                      const SizedBox(height: 20),
                      BlocBuilder<EarningsCubit, EarningsState>(
                        builder: (context, earningsState) => EarningsPill(amount: earningsState.summary?.today ?? 1250),
                      ),
                      const SizedBox(height: 28),
                      const SectionHeader(title: 'Entrega activa'),
                      const Row(
                        mainAxisAlignment: MainAxisAlignment.end,
                        children: [
                          Icon(Icons.circle, size: 9, color: AppColors.primary),
                          SizedBox(width: 6),
                          Text('EM CURSO', style: TextStyle(color: AppColors.primary, fontWeight: FontWeight.w900)),
                        ],
                      ),
                      const SizedBox(height: 10),
                      if (state.activeDelivery != null) ActiveDeliveryCard(delivery: state.activeDelivery!),
                      const SizedBox(height: 28),
                      const SectionHeader(title: 'Entregas disponíveis'),
                      const SizedBox(height: 10),
                      ...state.availableJobs.map((job) => Padding(
                            padding: const EdgeInsets.only(bottom: 16),
                            child: AvailableJobCard(
                              job: job,
                              onAccept: () => context.read<DashboardCubit>().acceptJob(job.id),
                              onReject: () => context.read<DashboardCubit>().rejectJob(job.id),
                            ),
                          )),
                    ],
                  );
                },
              ),
              const SizedBox(height: 12),
              BlocBuilder<EarningsCubit, EarningsState>(
                builder: (context, state) {
                  if (state.weekly.isEmpty) return const SizedBox.shrink();
                  return WeeklyChartCard(weekly: state.weekly);
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}
