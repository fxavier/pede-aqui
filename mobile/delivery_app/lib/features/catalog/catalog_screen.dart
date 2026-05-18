import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class CatalogSku {
  CatalogSku({
    required this.id,
    required this.name,
    required this.price,
    this.available = true,
    this.pharmacyProduct = false,
    this.prohibitedFuel = false,
  });
  final String id;
  final String name;
  final double price;
  final bool available;
  final bool pharmacyProduct;
  final bool prohibitedFuel;
}

class CatalogProduct {
  CatalogProduct({required this.id, required this.name, required this.skus});
  final String id;
  final String name;
  final List<CatalogSku> skus;
}

class CatalogState {
  const CatalogState({this.loading = false, this.products = const [], this.error, this.forbidden = false});
  final bool loading;
  final List<CatalogProduct> products;
  final String? error;
  final bool forbidden;
}

abstract class CatalogApi {
  Future<dynamic> loadVendorProducts(String vendorId);
}

class CatalogCubit extends Cubit<CatalogState> {
  CatalogCubit(this.api) : super(const CatalogState());
  final CatalogApi api;

  Future<void> load(String vendorId) async {
    emit(const CatalogState(loading: true));
    final result = await api.loadVendorProducts(vendorId);
    if (result.forbidden == true) return emit(const CatalogState(forbidden: true));
    if (result.ok != true) return emit(CatalogState(error: result.error as String?));
    final products = ((result.data as List<dynamic>?) ?? const [])
        .map((entry) => Map<String, dynamic>.from(entry as Map))
        .map(
          (entry) => CatalogProduct(
            id: (entry['id'] ?? '').toString(),
            name: (entry['name'] ?? 'Produto').toString(),
            skus: ((entry['skus'] as List<dynamic>?) ?? const [])
                .map((skuEntry) => Map<String, dynamic>.from(skuEntry as Map))
                .map(
                  (skuEntry) => CatalogSku(
                    id: (skuEntry['id'] ?? '').toString(),
                    name: (skuEntry['name'] ?? 'SKU').toString(),
                    price: (skuEntry['price'] as num?)?.toDouble() ?? 0,
                    available: (skuEntry['available'] as bool?) ?? true,
                    pharmacyProduct: (skuEntry['pharmacyProduct'] as bool?) ?? false,
                    prohibitedFuel: (skuEntry['prohibitedFuel'] as bool?) ?? false,
                  ),
                )
                .toList(),
          ),
        )
        .toList();
    emit(CatalogState(products: products));
  }
}

class CatalogScreen extends StatelessWidget {
  const CatalogScreen({
    super.key,
    required this.cubit,
    required this.selectedVendorId,
    required this.selectedSkuId,
    required this.onSkuSelected,
  });

  final CatalogCubit cubit;
  final String? selectedVendorId;
  final String? selectedSkuId;
  final ValueChanged<String> onSkuSelected;

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<CatalogCubit, CatalogState>(
      bloc: cubit,
      builder: (context, state) => ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text('Fornecedor ativo: ${selectedVendorId ?? '-'}'),
          const SizedBox(height: 8),
          if (state.loading) const CircularProgressIndicator(),
          if (state.forbidden) const Text('Sem permissao para catalogo.'),
          if (state.error != null) Text('Erro: ${state.error}'),
          if (!state.loading && state.products.isEmpty) const Text('Sem SKUs ativos para este fornecedor.'),
          ...state.products.expand(
            (product) => product.skus.map(
              (sku) => ListTile(
                title: Text(product.name),
                subtitle: Text(
                  '${sku.name} | ${(sku.available ? 'Disponivel' : 'Indisponivel')}${sku.pharmacyProduct ? ' | Requer receita' : ''}${sku.prohibitedFuel ? ' | Combustivel bloqueado' : ''}',
                ),
                trailing: selectedSkuId == sku.id ? const Icon(Icons.check_circle, color: Colors.green) : Text('MZN ${sku.price.toStringAsFixed(2)}'),
                onTap: (!sku.available || sku.prohibitedFuel) ? null : () => onSkuSelected(sku.id),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
