import 'package:flutter/foundation.dart';
import 'package:get_it/get_it.dart';

import '../../features/auth/data/auth_repository.dart';
import '../../features/auth/presentation/auth_cubit.dart';
import '../../features/cart/data/cart_repository.dart';
import '../../features/cart/presentation/cart_cubit.dart';
import '../../features/catalog/data/catalog_repository.dart';
import '../../features/catalog/presentation/catalog_cubit.dart';
import '../../features/orders/data/order_repository.dart';
import '../../features/orders/presentation/order_tracking_cubit.dart';
import '../network/api_client.dart';
import '../storage/token_storage.dart';

final getIt = GetIt.instance;

void setupServiceLocator() {
  if (getIt.isRegistered<ApiClient>()) return;

  const useMock = bool.fromEnvironment('USE_MOCK_DATA', defaultValue: true);

  // Mock data must never ship to end users: fail loudly instead of silently
  // serving fake catalogs and a fake login in a release build.
  if (kReleaseMode && useMock) {
    throw StateError(
      'USE_MOCK_DATA is enabled in a release build. '
      'Build with --dart-define=USE_MOCK_DATA=false (and configure API_BASE_URL / KEYCLOAK_ISSUER) '
      'to run against the real backend.',
    );
  }

  getIt.registerLazySingleton<ApiClient>(() => ApiClient());
  getIt.registerLazySingleton<TokenStorage>(() => SecureTokenStorage());

  if (useMock) {
    getIt.registerLazySingleton<AuthRepository>(() => MockAuthRepository());
    getIt.registerLazySingleton<CatalogRepository>(() => MockCatalogRepository());
    getIt.registerLazySingleton<CartRepository>(() => MockCartRepository());
    getIt.registerLazySingleton<OrderRepository>(() => MockOrderRepository());
  } else {
    getIt.registerLazySingleton<AuthRepository>(
        () => ApiAuthRepository(getIt<ApiClient>(), getIt<TokenStorage>()));
    getIt.registerLazySingleton<CatalogRepository>(() => ApiCatalogRepository(getIt<ApiClient>()));
    // Customer id comes from the authenticated session (Keycloak user id),
    // resolved lazily so the repository can be registered before login.
    getIt.registerLazySingleton<CartRepository>(() => ApiCartRepository(
          getIt<ApiClient>(),
          () => (getIt<AuthRepository>() as ApiAuthRepository).customerId,
        ));
    getIt.registerLazySingleton<OrderRepository>(() => ApiOrderRepository(getIt<ApiClient>()));

    // Wire refresh interceptor
    final apiClient = getIt<ApiClient>();
    final authRepo = getIt<AuthRepository>() as ApiAuthRepository;
    apiClient.onRefreshToken = () => authRepo.refreshToken();
  }

  getIt.registerFactory(() => AuthCubit(getIt<AuthRepository>()));
  getIt.registerFactory(() => CatalogCubit(getIt<CatalogRepository>()));
  getIt.registerFactory(() => CartCubit(getIt<CartRepository>()));
  getIt.registerFactory(() => OrderTrackingCubit(getIt<OrderRepository>()));
}
