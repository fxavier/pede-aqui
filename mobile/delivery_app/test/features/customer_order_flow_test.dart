import 'package:flutter_test/flutter_test.dart';
import 'package:pede_aqui_delivery_app/core/api/api_client.dart';
import 'package:pede_aqui_delivery_app/features/cart/cart_screen.dart';
import 'package:pede_aqui_delivery_app/features/checkout/checkout_screen.dart';
import 'package:pede_aqui_delivery_app/features/orders/orders_screen.dart';
import 'package:pede_aqui_delivery_app/features/vendor_discovery/vendor_discovery_screen.dart';

class FakeApiClient extends ApiClient {
  FakeApiClient() : super(baseUrl: 'http://localhost', tokenProvider: () => 'token');

  ApiResult<dynamic>? nextGet;
  ApiResult<dynamic>? nextPost;

  @override
  Future<ApiResult<dynamic>> getJson(String path) async {
    return nextGet ?? ApiResult.error('missing fake get response');
  }

  @override
  Future<ApiResult<dynamic>> postJson(String path, Map<String, dynamic> body) async {
    return nextPost ?? ApiResult.error('missing fake post response');
  }
}

void main() {
  group('US1 customer flow cubits', () {
    test('vendor discovery transitions loading to success', () async {
      final api = FakeApiClient()
        ..nextGet = ApiResult.success([
          {'id': 'v1', 'name': 'Mercado da Baixa', 'available': true, 'rating': 4.7}
        ]);
      final cubit = VendorDiscoveryCubit(api);

      await cubit.load(category: 'grocery', onlyAvailable: true, minRating: 4.0);

      expect(cubit.state.loading, false);
      expect(cubit.state.items.length, 1);
    });

    test('cart rule rejects mixed vendor items', () {
      final cubit = CartCubit(FakeApiClient());
      cubit.add(CartItem(vendorId: 'v1', name: 'A', price: 1));
      cubit.add(CartItem(vendorId: 'v2', name: 'B', price: 1));
      expect(cubit.state.error, isNotNull);
    });

    test('checkout transitions loading to success', () async {
      final api = FakeApiClient()
        ..nextPost = ApiResult.success({'id': 'o-1', 'reference': 'PA-1'});
      final cubit = CheckoutCubit(api);
      await cubit.checkout(
        cartId: 'c-1',
        fulfillmentType: 'DELIVERY',
        deliveryInstructions: 'Tocar campainha',
      );
      expect(cubit.state.successOrderId, 'o-1');
      expect(cubit.state.successRef, 'PA-1');
    });

    test('order status transitions from loading to success', () async {
      final api = FakeApiClient()
        ..nextGet = ApiResult.success({
          'id': 'o-1',
          'reference': 'PA-1',
          'orderStatus': 'OUT_FOR_DELIVERY',
          'deliveryCode': '123456',
        });
      final cubit = OrdersCubit(api);
      await cubit.loadById('o-1');
      expect(cubit.state.error, isNull);
      expect(cubit.state.items.first['orderStatus'], 'OUT_FOR_DELIVERY');
    });
  });
}
