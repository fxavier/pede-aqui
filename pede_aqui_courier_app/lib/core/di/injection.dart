import 'package:get_it/get_it.dart';

import '../../data/datasources/auth_data_source.dart';
import '../../data/datasources/courier_data_source.dart';
import '../../data/datasources/mock_auth_data_source.dart';
import '../../data/datasources/mock_courier_data_source.dart';
import '../../data/datasources/remote_auth_data_source.dart';
import '../../data/datasources/remote_courier_data_source.dart';
import '../../data/repositories/auth_repository.dart';
import '../../data/repositories/courier_repository.dart';
import '../../presentation/cubits/auth/auth_cubit.dart';
import '../network/api_client.dart';

final GetIt getIt = GetIt.instance;

void configureDependencies() {
  if (getIt.isRegistered<CourierRepository>()) {
    return;
  }

  getIt.registerLazySingleton<ApiClient>(() => ApiClient());
  
  // Auth services
  getIt.registerLazySingleton<AuthDataSource>(() => _createAuthDataSource());
  getIt.registerLazySingleton<AuthRepository>(() => AuthRepositoryImpl(getIt<AuthDataSource>()));
  getIt.registerFactory<AuthCubit>(() => AuthCubit(getIt<AuthRepository>()));
  
  // Courier services
  getIt.registerLazySingleton<CourierDataSource>(() => _createCourierDataSource());
  getIt.registerLazySingleton<CourierRepository>(() => CourierRepositoryImpl(getIt<CourierDataSource>()));
}

AuthDataSource _createAuthDataSource() {
  final useMock = const bool.fromEnvironment('USE_MOCK_DATA', defaultValue: true);
  if (useMock) {
    return MockAuthDataSource();
  }
  return RemoteAuthDataSource(getIt<ApiClient>());
}

CourierDataSource _createCourierDataSource() {
  final useMock = const bool.fromEnvironment('USE_MOCK_DATA', defaultValue: true);
  if (useMock) {
    return MockCourierDataSource();
  }
  return RemoteCourierDataSource(getIt<ApiClient>());
}
