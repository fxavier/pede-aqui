import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:provider/provider.dart';

import 'core/providers/app_settings_provider.dart';
import 'core/theme/app_theme.dart';
import 'presentation/screens/delivery_confirmation_screen.dart';
import 'presentation/screens/delivery_detail_screen.dart';
import 'presentation/screens/login_screen.dart';
import 'presentation/screens/notifications_screen.dart';
import 'presentation/screens/onboarding_screen.dart';
import 'presentation/screens/shell_screen.dart';
import 'presentation/screens/settings_screen.dart';
import 'presentation/screens/wallet_screen.dart';

class PedeAquiCourierApp extends StatelessWidget {
  const PedeAquiCourierApp({super.key});

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<AppSettingsProvider>();

    return MaterialApp(
      title: 'Pede Aqui Estafeta',
      debugShowCheckedModeBanner: false,
      themeMode: settings.themeMode,
      theme: AppTheme.light(),
      darkTheme: AppTheme.light(),
      locale: const Locale('pt', 'MZ'),
      supportedLocales: const [Locale('pt', 'MZ'), Locale('pt', 'PT')],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      initialRoute: AppRoutes.shell,
      routes: {
        AppRoutes.onboarding: (_) => const OnboardingScreen(),
        AppRoutes.login: (_) => const LoginScreen(),
        AppRoutes.shell: (_) => const ShellScreen(),
        AppRoutes.deliveryDetail: (_) => const DeliveryDetailScreen(),
        AppRoutes.confirmDelivery: (_) => const DeliveryConfirmationScreen(),
        AppRoutes.notifications: (_) => const NotificationsScreen(),
        AppRoutes.wallet: (_) => const WalletScreen(),
        AppRoutes.settings: (_) => const SettingsScreen(),
      },
    );
  }
}

abstract final class AppRoutes {
  static const onboarding = '/onboarding';
  static const login = '/login';
  static const shell = '/app';
  static const deliveryDetail = '/delivery-detail';
  static const confirmDelivery = '/confirm-delivery';
  static const notifications = '/notifications';
  static const wallet = '/wallet';
  static const settings = '/settings';
}
