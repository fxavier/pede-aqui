import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../app.dart';
import '../cubits/dashboard/dashboard_cubit.dart';
import '../cubits/delivery/delivery_cubit.dart';
import '../cubits/earnings/earnings_cubit.dart';
import '../cubits/location_consent/location_consent_cubit.dart';
import '../cubits/profile/profile_cubit.dart';
import '../widgets/bottom_navigation.dart';
import 'earnings_screen.dart';
import 'history_screen.dart';
import 'home_dashboard_screen.dart';
import 'profile_screen.dart';

class ShellScreen extends StatefulWidget {
  const ShellScreen({super.key});

  @override
  State<ShellScreen> createState() => _ShellScreenState();
}

class _ShellScreenState extends State<ShellScreen> {
  int _index = 0;

  final List<Widget> _screens = const [
    HomeDashboardScreen(),
    HistoryScreen(),
    EarningsScreen(),
    ProfileScreen(),
  ];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _onSignedIn());
  }

  /// Loads the signed-in courier's data and, at onboarding (no valid consent to the current text version), asks for
  /// the GPS consent before anything else (LAC-40).
  Future<void> _onSignedIn() async {
    if (!mounted) return;
    final consentCubit = context.read<LocationConsentCubit>();
    context.read<DashboardCubit>().loadDashboard();
    context.read<DeliveryCubit>().loadActiveDelivery();
    context.read<EarningsCubit>().loadEarnings().catchError((Object _) {});
    context.read<ProfileCubit>().loadProfile().catchError((Object _) {});

    await consentCubit.load();
    if (!mounted) return;
    if (consentCubit.state.isLoaded && !consentCubit.state.isValid) {
      await Navigator.of(context).pushNamed<bool>(AppRoutes.locationConsent);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(index: _index, children: _screens),
      bottomNavigationBar: PedeAquiBottomNavigation(currentIndex: _index, onChanged: (value) => setState(() => _index = value)),
    );
  }
}
