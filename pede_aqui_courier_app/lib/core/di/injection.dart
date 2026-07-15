import 'package:flutter/foundation.dart';
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
import '../storage/token_storage.dart';

final GetIt getIt = GetIt.instance;

const bool _useMock = bool.fromEnvironment('USE_MOCK_DATA', defaultValue: true);

void configureDependencies() {
  if (getIt.isRegistered<CourierRepository>()) {
    return;
  }

  // Mock data must never ship to end users: fail loudly instead of silently
  // serving fake deliveries and a fake login in a release build.
  if (kReleaseMode && _useMock) {
    throw StateError(
      'USE_MOCK_DATA is enabled in a release build. '
      'Build with --dart-define=USE_MOCK_DATA=false (and configure API_BASE_URL / KEYCLOAK_ISSUER) '
      'to run against the real backend.',
    );
  }

  getIt.registerLazySingleton<ApiClient>(() => ApiClient());
  getIt.registerLazySingleton<TokenStorage>(() => SecureTokenStorage());

  // Auth services
  getIt.registerLazySingleton<AuthDataSource>(() => _createAuthDataSource());
  getIt.registerLazySingleton<AuthRepository>(() => AuthRepositoryImpl(getIt<AuthDataSource>()));
  getIt.registerFactory<AuthCubit>(() => AuthCubit(getIt<AuthRepository>()));

  // Courier services
  getIt.registerLazySingleton<CourierDataSource>(() => _createCourierDataSource());
  getIt.registerLazySingleton<CourierRepository>(() => CourierRepositoryImpl(getIt<CourierDataSource>()));
}

AuthDataSource _createAuthDataSource() {
  if (_useMock) {
    return MockAuthDataSource();
  }
  return RemoteAuthDataSource(getIt<ApiClient>(), getIt<TokenStorage>());
}

CourierDataSource _createCourierDataSource() {
  if (_useMock) {
    return MockCourierDataSource();
  }
  return RemoteCourierDataSource(getIt<ApiClient>());
}
