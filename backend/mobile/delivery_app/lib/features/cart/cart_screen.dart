import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:pede_aqui_delivery_app/core/api/api_client.dart';
import 'package:pede_aqui_delivery_app/core/formatters/currency_formatter.dart';

class CartItem {
  CartItem({required this.vendorId, required this.name, required this.price});

  final String vendorId;
  final String name;
  final double price;
}

class CartState {
  const CartState({
    this.items = const [],
    this.error,
    this.loading = false,
    this.forbidden = false,
    this.cartId,
    this.totalFromApi,
  });

  final List<CartItem> items;
  final String? error;
  final bool loading;
  final bool forbidden;
  final String? cartId;
  final double? totalFromApi;

  double get total => totalFromApi ?? items.fold(0, (sum, item) => sum + item.price);
}

class CartCubit extends Cubit<CartState> {
  CartCubit(this.apiClient) : super(const CartState());

  final ApiClient apiClient;

  void add(CartItem item) {
    if (state.items.isNotEmpty && state.items.first.vendorId != item.vendorId) {
      emit(CartState(items: state.items, error: 'So e permitido um fornecedor por carrinho.'));
      return;
    }
    emit(CartState(items: [...state.items, item]));
  }

  Future<void> addFromApi({
    required String customerId,
    required String vendorId,
    required String skuId,
    required int quantity,
  }) async {
    emit(CartState(items: state.items, loading: true, cartId: state.cartId));
    final result = await apiClient.postJson('/customers/$customerId/cart/items', {
      'vendorId': vendorId,
      'skuId': skuId,
      'quantity': quantity,
    });
    if (result.forbidden) {
      emit(CartState(items: state.items, forbidden: true, cartId: state.cartId));
      return;
    }
    if (!result.ok) {
      emit(CartState(items: state.items, error: result.error, cartId: state.cartId));
      return;
    }

    final data = Map<String, dynamic>.from(result.data as Map);
    final cartId = data['id']?.toString();
    final apiTotal = (data['total'] as num?)?.toDouble();
    final items = ((data['items'] as List<dynamic>? ?? const []))
        .map((entry) => Map<String, dynamic>.from(entry as Map))
        .map((entry) => CartItem(
              vendorId: vendorId,
              name: (entry['productName'] ?? 'Item').toString(),
              price: (entry['lineTotal'] as num?)?.toDouble() ?? 0,
            ))
        .toList();
    emit(CartState(items: items, cartId: cartId, totalFromApi: apiTotal));
  }
}

class CartScreen extends StatelessWidget {
  const CartScreen({
    super.key,
    required this.cubit,
    required this.customerId,
    required this.selectedVendorId,
    required this.selectedSkuId,
  });

  final CartCubit cubit;
  final String customerId;
  final String? selectedVendorId;
  final String? selectedSkuId;

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<CartCubit, CartState>(
      bloc: cubit,
      builder: (context, state) {
        return Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Carrinho de um unico fornecedor', style: TextStyle(fontWeight: FontWeight.w700)),
              Text('Cart ID: ${state.cartId ?? '-'}'),
              Text('Cliente ativo: $customerId'),
              Text('Fornecedor selecionado: ${selectedVendorId ?? '-'}'),
              Text('SKU selecionado: ${selectedSkuId ?? '-'}'),
              const SizedBox(height: 8),
              FilledButton(
                onPressed: () {
                  if (selectedVendorId == null || selectedSkuId == null) {
                    return;
                  }
                  cubit.addFromApi(
                    customerId: customerId,
                    vendorId: selectedVendorId!,
                    skuId: selectedSkuId!,
                    quantity: 1,
                  );
                },
                child: const Text('Adicionar ao carrinho (1 unidade)'),
              ),
              if (state.loading) const LinearProgressIndicator(),
              if (state.forbidden) const Text('Sem permissao para alterar carrinho.'),
              if (state.error != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(state.error!, style: const TextStyle(color: Colors.red)),
                ),
              const SizedBox(height: 8),
              if (state.items.isEmpty) const Text('Carrinho vazio.'),
              ...state.items.map((item) => ListTile(title: Text(item.name), trailing: Text(formatMzn(item.price)))),
              const Divider(),
              Text('Total estimado: ${formatMzn(state.total)}'),
            ],
          ),
        );
      },
    );
  }
}
