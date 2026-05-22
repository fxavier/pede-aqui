import 'package:flutter/material.dart';
import 'package:delivery_marketplace_mobile/core/config/app_config.dart';
import 'package:delivery_marketplace_mobile/features/cart/cart_screen.dart';
import 'package:delivery_marketplace_mobile/features/catalog/catalog_screen.dart';
import 'package:delivery_marketplace_mobile/features/checkout/checkout_screen.dart';
import 'package:delivery_marketplace_mobile/features/courier_jobs/courier_jobs_screen.dart';
import 'package:delivery_marketplace_mobile/features/customer_home/customer_home_screen.dart';
import 'package:delivery_marketplace_mobile/features/delivery_confirmation/delivery_confirmation_screen.dart';
import 'package:delivery_marketplace_mobile/features/orders/orders_screen.dart';
import 'package:delivery_marketplace_mobile/features/profile/profile_screen.dart';
import 'package:delivery_marketplace_mobile/features/vendor_discovery/vendor_discovery_screen.dart';

void main() {
  runApp(const DeliveryMarketplaceApp());
}

/// Root widget for the delivery marketplace mobile app.
class DeliveryMarketplaceApp extends StatelessWidget {
  const DeliveryMarketplaceApp({super.key});

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
      home: const MobileShell(),
    );
  }
}

class MobileShell extends StatefulWidget {
  const MobileShell({super.key});

  @override
  State<MobileShell> createState() => _MobileShellState();
}

class _MobileShellState extends State<MobileShell> {
  int _index = 0;

  final List<Widget> _tabs = const [
    CustomerHomeScreen(),
    VendorDiscoveryScreen(),
    CatalogScreen(),
    CartScreen(),
    CheckoutScreen(),
    OrdersScreen(),
    CourierJobsScreen(),
    DeliveryConfirmationScreen(),
    ProfileScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Pede Aqui - Mozambique'),
      ),
      body: _tabs[_index],
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (value) => setState(() => _index = value),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.home_outlined), label: 'Inicio'),
          NavigationDestination(icon: Icon(Icons.storefront_outlined), label: 'Lojas'),
          NavigationDestination(icon: Icon(Icons.list_alt_outlined), label: 'Catalogo'),
          NavigationDestination(icon: Icon(Icons.shopping_cart_outlined), label: 'Carrinho'),
          NavigationDestination(icon: Icon(Icons.receipt_long_outlined), label: 'Checkout'),
          NavigationDestination(icon: Icon(Icons.history_outlined), label: 'Pedidos'),
          NavigationDestination(icon: Icon(Icons.delivery_dining_outlined), label: 'Estafeta'),
          NavigationDestination(icon: Icon(Icons.pin_outlined), label: 'Codigo'),
          NavigationDestination(icon: Icon(Icons.person_outline), label: 'Perfil'),
        ],
      ),
    );
  }
}
