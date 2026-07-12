import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:provider/provider.dart';

import 'app/pede_aqui_app.dart';
import 'core/di/service_locator.dart';
import 'features/auth/presentation/auth_cubit.dart';
import 'features/cart/presentation/cart_cubit.dart';
import 'features/catalog/presentation/catalog_cubit.dart';
import 'features/orders/presentation/order_tracking_cubit.dart';
import 'features/settings/app_settings_provider.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  setupServiceLocator();

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => AppSettingsProvider()),
      ],
      child: MultiBlocProvider(
        providers: [
          BlocProvider(create: (_) => getIt<AuthCubit>()..restoreSession()),
          BlocProvider(create: (_) => getIt<CatalogCubit>()..loadHome()),
          BlocProvider(create: (_) => getIt<CartCubit>()..loadCart()),
          BlocProvider(create: (_) => getIt<OrderTrackingCubit>()),
        ],
        child: const PedeAquiApp(),
      ),
    ),
  );
}
