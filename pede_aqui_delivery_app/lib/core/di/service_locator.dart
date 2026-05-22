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

final getIt = GetIt.instance;

void setupServiceLocator() {
  if (getIt.isRegistered<ApiClient>()) return;

  const useMock = bool.fromEnvironment('USE_MOCK_DATA', defaultValue: true);

  getIt.registerLazySingleton<ApiClient>(() => ApiClient());

  if (useMock) {
    getIt.registerLazySingleton<AuthRepository>(() => MockAuthRepository());
    getIt.registerLazySingleton<CatalogRepository>(() => MockCatalogRepository());
    getIt.registerLazySingleton<CartRepository>(() => MockCartRepository());
    getIt.registerLazySingleton<OrderRepository>(() => MockOrderRepository());
  } else {
    getIt.registerLazySingleton<AuthRepository>(() => ApiAuthRepository(getIt<ApiClient>()));
    getIt.registerLazySingleton<CatalogRepository>(() => ApiCatalogRepository(getIt<ApiClient>()));
    // TODO: Cart repository uses hardcoded customer-id - update when user management is implemented
    getIt.registerLazySingleton<CartRepository>(() => ApiCartRepository(getIt<ApiClient>(), 'customer-id'));
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
