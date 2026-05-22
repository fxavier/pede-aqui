import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:provider/provider.dart';

import '../features/auth/presentation/login_register_screen.dart';
import '../features/cart/presentation/cart_screen.dart';
import '../features/catalog/presentation/home_screen.dart';
import '../features/catalog/presentation/landing_screen.dart';
import '../features/catalog/presentation/onboarding_screen.dart';
import '../features/catalog/presentation/store_screen.dart';
import '../features/checkout/presentation/checkout_promotion_screen.dart';
import '../features/checkout/presentation/checkout_screen.dart';
import '../features/orders/presentation/order_tracking_screen.dart';
import '../features/settings/app_settings_provider.dart';
import 'theme.dart';

class AppRoutes {
  static const landing = '/landing';
  static const onboarding = '/onboarding';
  static const auth = '/auth';
  static const home = '/home';
  static const store = '/store';
  static const cart = '/cart';
  static const checkout = '/checkout';
  static const checkoutPromo = '/checkout-promo';
  static const orderTracking = '/order-tracking';

  static const Map<String, String> screenMap = {
    'pede_aqui_landing_page': landing,
    'onboarding_pede_aqui': onboarding,
    'login_register_pede_aqui': auth,
    'home_pede_aqui': home,
    'store_pede_aqui': store,
    'cart_pede_aqui': cart,
    'checkout_pede_aqui': checkout,
    'checkout_with_promotion_pede_aqui': checkoutPromo,
    'order_tracking_pede_aqui': orderTracking,
  };
}

class PedeAquiApp extends StatelessWidget {
  const PedeAquiApp({super.key});

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<AppSettingsProvider>();

    return MaterialApp(
      title: 'Pede Aqui',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      themeMode: settings.themeMode,
      locale: const Locale('pt', 'MZ'),
      supportedLocales: const [Locale('pt', 'MZ'), Locale('pt', 'PT')],
      localizationsDelegates: [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      initialRoute: AppRoutes.onboarding,
      routes: {
        AppRoutes.landing: (_) => const LandingScreen(),
        AppRoutes.onboarding: (_) => const OnboardingScreen(),
        AppRoutes.auth: (_) => const LoginRegisterScreen(),
        AppRoutes.home: (_) => const HomeScreen(),
        AppRoutes.store: (_) => const StoreScreen(),
        AppRoutes.cart: (_) => const CartScreen(),
        AppRoutes.checkout: (_) => const CheckoutScreen(),
        AppRoutes.checkoutPromo: (_) => const CheckoutPromotionScreen(),
        AppRoutes.orderTracking: (_) => const OrderTrackingScreen(),
      },
    );
  }
}
