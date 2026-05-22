import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:pede_aqui_delivery_app/core/api/api_client.dart';
import 'package:pede_aqui_delivery_app/core/auth/auth_cubit.dart';
import 'package:pede_aqui_delivery_app/core/auth/keycloak_auth_service.dart';
import 'package:pede_aqui_delivery_app/core/config/app_config.dart';
import 'package:pede_aqui_delivery_app/features/auth/auth_screen.dart';
import 'package:pede_aqui_delivery_app/features/cart/cart_screen.dart';
import 'package:pede_aqui_delivery_app/features/catalog/catalog_screen.dart';
import 'package:pede_aqui_delivery_app/features/checkout/checkout_screen.dart';
import 'package:pede_aqui_delivery_app/features/orders/orders_screen.dart';
import 'package:pede_aqui_delivery_app/features/profile/address_screen.dart';
import 'package:pede_aqui_delivery_app/features/vendor_discovery/vendor_discovery_screen.dart';

void main() {
  runApp(const DeliveryCustomerApp());
}

class DeliveryCustomerApp extends StatelessWidget {
  const DeliveryCustomerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: AppConfig.appName,
      locale: const Locale('pt', 'MZ'),
      theme: ThemeData(
        colorSchemeSeed: const Color(0xFF00A586),
        scaffoldBackgroundColor: const Color(0xFFF2FBFA),
        useMaterial3: true,
      ),
      home: const DeliveryHomeShell(),
    );
  }
}

class DeliveryHomeShell extends StatefulWidget {
  const DeliveryHomeShell({super.key});

  @override
  State<DeliveryHomeShell> createState() => _DeliveryHomeShellState();
}

class _DeliveryHomeShellState extends State<DeliveryHomeShell> {
  int _index = 0;
  String? _customerId;
  String? _selectedVendorId;
  String? _selectedSkuId;
  bool _authenticated = false;

  late final ApiClient _apiClient;
  late final AuthCubit _authCubit;
  late final VendorDiscoveryCubit _vendorDiscoveryCubit;
  late final CartCubit _cartCubit;
  late final CatalogCubit _catalogCubit;
  late final AddressCubit _addressCubit;
  late final MeCubit _meCubit;
  late final CheckoutCubit _checkoutCubit;
  late final OrdersCubit _ordersCubit;
  StreamSubscription<CartState>? _cartSubscription;

  @override
  void initState() {
    super.initState();
    _authCubit = AuthCubit(KeycloakAuthService());
    _apiClient = ApiClient(
      baseUrl: AppConfig.apiBaseUrl,
      tokenProvider: () => _authCubit.state.token ?? const String.fromEnvironment('APP_TOKEN', defaultValue: 'dev-token'),
    );
    _meCubit = MeCubit(_apiClient)..load();
    _vendorDiscoveryCubit = VendorDiscoveryCubit(_apiClient)..load();
    _catalogCubit = CatalogCubit(_CatalogApiAdapter(_apiClient));
    _cartCubit = CartCubit(_apiClient);
    _addressCubit = AddressCubit()..loadDefaults();
    _checkoutCubit = CheckoutCubit(_apiClient);
    _ordersCubit = OrdersCubit(_apiClient);
    _cartSubscription = _cartCubit.stream.listen((_) {
      if (mounted) setState(() {});
    });
    _meCubit.stream.listen((state) {
      if (state.customerId != null && mounted) {
        setState(() {
          _customerId = state.customerId;
        });
      }
    });
  }

  @override
  void dispose() {
    _cartSubscription?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!_authenticated) {
      return AuthScreen(
        cubit: _authCubit,
        onAuthenticated: () {
          setState(() {
            _authenticated = true;
          });
          _meCubit.load();
          _vendorDiscoveryCubit.load();
        },
      );
    }

    final screens = [
      VendorDiscoveryScreen(
        cubit: _vendorDiscoveryCubit,
        selectedVendorId: _selectedVendorId,
        onVendorSelected: (vendorId) {
          setState(() {
            _selectedVendorId = vendorId;
            _selectedSkuId = null;
            _index = 1;
          });
          _catalogCubit.load(vendorId);
        },
      ),
      CatalogScreen(
        cubit: _catalogCubit,
        selectedVendorId: _selectedVendorId,
        selectedSkuId: _selectedSkuId,
        onSkuSelected: (skuId) async {
          if (_customerId == null || _selectedVendorId == null) return;
          setState(() {
            _selectedSkuId = skuId;
          });
          await _cartCubit.addFromApi(
            customerId: _customerId!,
            vendorId: _selectedVendorId!,
            skuId: skuId,
            quantity: 1,
          );
          if (mounted) {
            setState(() {
              _index = 2;
            });
          }
        },
      ),
      CartScreen(
        cubit: _cartCubit,
        customerId: _customerId ?? '-',
        selectedVendorId: _selectedVendorId,
        selectedSkuId: _selectedSkuId,
      ),
      AddressScreen(cubit: _addressCubit),
      CheckoutScreen(
        cubit: _checkoutCubit,
        selectedCartId: _cartCubit.state.cartId,
        onOrderCreated: (orderId) {
          _ordersCubit.loadById(orderId);
          setState(() => _index = 4);
        },
      ),
      OrdersScreen(cubit: _ordersCubit),
    ];

    return Scaffold(
      appBar: AppBar(title: const Text('Pede Aqui Cliente - Mocambique')),
      body: screens[_index],
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (value) => setState(() => _index = value),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.storefront_outlined), label: 'Lojas'),
          NavigationDestination(icon: Icon(Icons.list_alt_outlined), label: 'Catalogo'),
          NavigationDestination(icon: Icon(Icons.shopping_cart_outlined), label: 'Carrinho'),
          NavigationDestination(icon: Icon(Icons.location_on_outlined), label: 'Moradas'),
          NavigationDestination(icon: Icon(Icons.receipt_long_outlined), label: 'Checkout'),
          NavigationDestination(icon: Icon(Icons.history_outlined), label: 'Pedidos'),
        ],
      ),
    );
  }
}

class MeState {
  const MeState({this.loading = false, this.customerId, this.error, this.forbidden = false});
  final bool loading;
  final String? customerId;
  final String? error;
  final bool forbidden;
}

class MeCubit extends Cubit<MeState> {
  MeCubit(this.apiClient) : super(const MeState());
  final ApiClient apiClient;

  Future<void> load() async {
    emit(const MeState(loading: true));
    final result = await apiClient.getJson('/me');
    if (result.forbidden) return emit(const MeState(forbidden: true));
    if (!result.ok) return emit(MeState(error: result.error));
    emit(MeState(customerId: (result.data['id'] ?? '').toString()));
  }
}


class _CatalogApiAdapter implements CatalogApi {
  _CatalogApiAdapter(this.apiClient);

  final ApiClient apiClient;

  @override
  Future<dynamic> loadVendorProducts(String vendorId) {
    return apiClient.getJson('/catalog/vendors/$vendorId/products');
  }
}
