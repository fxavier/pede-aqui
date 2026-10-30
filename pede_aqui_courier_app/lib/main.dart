import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:provider/provider.dart';

import 'app.dart';
import 'core/di/injection.dart';
import 'core/location/location_sender.dart';
import 'core/network/api_client.dart';
import 'core/providers/app_settings_provider.dart';
import 'data/datasources/location_data_source.dart';
import 'data/repositories/courier_repository.dart';
import 'presentation/app_event_handlers.dart';
import 'presentation/cubits/dashboard/dashboard_cubit.dart';
import 'presentation/cubits/delivery/delivery_cubit.dart';
import 'presentation/cubits/earnings/earnings_cubit.dart';
import 'presentation/cubits/location_consent/location_consent_cubit.dart';
import 'presentation/cubits/profile/profile_cubit.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
  configureDependencies();

  final sender = getIt<LocationSender>();
  final consentCubit = LocationConsentCubit(getIt<LocationDataSource>(), sender);
  bindAppEventHandlers(apiClient: getIt<ApiClient>(), sender: sender, consentCubit: consentCubit);

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => AppSettingsProvider()),
      ],
      child: MultiBlocProvider(
        providers: [
          BlocProvider(create: (_) => DashboardCubit(getIt<CourierRepository>(), sender: sender)),
          BlocProvider(create: (_) => DeliveryCubit(getIt<CourierRepository>())),
          BlocProvider(create: (_) => EarningsCubit(getIt<CourierRepository>())),
          BlocProvider(create: (_) => ProfileCubit(getIt<CourierRepository>())),
          BlocProvider.value(value: consentCubit),
        ],
        child: const PedeAquiCourierApp(),
      ),
    ),
  );
}
