import '../../../core/network/api_client.dart';
import 'cart_models.dart';

abstract class CartRepository {
  Future<CartSummary> getCart();
  Future<CartSummary> updateQuantity(String productId, int quantity);
}

class MockCartRepository implements CartRepository {
  List<CartItem> _items = const [
    CartItem(productId: 'chamucas-carne', name: 'Chamuças de Carne', price: 120, quantity: 2, emoji: '🥟'),
    CartItem(productId: 'asas-peri-peri', name: 'Asas Peri-Peri', price: 450, quantity: 1, emoji: '🍗'),
  ];

  @override
  Future<CartSummary> getCart() async {
    await Future<void>.delayed(const Duration(milliseconds: 250));
    return CartSummary(items: _items, deliveryFee: 150, taxes: 103.50);
  }

  @override
  Future<CartSummary> updateQuantity(String productId, int quantity) async {
    await Future<void>.delayed(const Duration(milliseconds: 150));
    _items = _items
        .map((item) => item.productId == productId ? item.copyWith(quantity: quantity.clamp(1, 99).toInt()) : item)
        .toList();
    return CartSummary(items: _items, deliveryFee: 150, taxes: 103.50);
  }
}

class ApiCartRepository implements CartRepository {
  ApiCartRepository(this._apiClient, this._customerIdProvider);

  final ApiClient _apiClient;

  /// Resolves the authenticated customer's id lazily from the auth session;
  /// throws a clear StateError when called before login.
  final String Function() _customerIdProvider;

  @override
  Future<CartSummary> getCart() async {
    final customerId = _customerIdProvider();
    final pricingResponse = await _apiClient.get<Map<String, dynamic>>(
      '/customers/$customerId/cart/pricing',
    );
    final pricing = pricingResponse.data!;
    return CartSummary(
      items: [],
      deliveryFee: (pricing['deliveryFee'] as num?)?.toDouble() ?? 0,
      taxes: (pricing['taxes'] as num?)?.toDouble() ?? 0,
      discount: (pricing['discounts'] as num?)?.toDouble() ?? 0,
    );
  }

  @override
  Future<CartSummary> updateQuantity(String productId, int quantity) async {
    if (quantity > 0) {
      await _apiClient.post<Map<String, dynamic>>(
        '/customers/${_customerIdProvider()}/cart/items',
        data: {'productId': productId, 'quantity': quantity},
      );
    }
    return getCart();
  }
}
