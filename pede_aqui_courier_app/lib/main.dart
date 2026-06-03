import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:provider/provider.dart';

import 'app.dart';
import 'core/di/injection.dart';
import 'core/providers/app_settings_provider.dart';
import 'data/repositories/courier_repository.dart';
import 'presentation/cubits/dashboard/dashboard_cubit.dart';
import 'presentation/cubits/delivery/delivery_cubit.dart';
import 'presentation/cubits/earnings/earnings_cubit.dart';
import 'presentation/cubits/profile/profile_cubit.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
  configureDependencies();

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => AppSettingsProvider()),
      ],
      child: MultiBlocProvider(
        providers: [
          BlocProvider(create: (_) => DashboardCubit(getIt<CourierRepository>())),
          BlocProvider(create: (_) => DeliveryCubit(getIt<CourierRepository>())),
          BlocProvider(create: (_) => EarningsCubit(getIt<CourierRepository>())),
          BlocProvider(create: (_) => ProfileCubit(getIt<CourierRepository>())),
        ],
        child: const PedeAquiCourierApp(),
      ),
    ),
  );
}
