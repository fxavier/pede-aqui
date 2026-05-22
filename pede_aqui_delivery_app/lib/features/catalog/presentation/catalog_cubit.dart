import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../data/catalog_models.dart';
import '../data/catalog_repository.dart';

class CatalogState extends Equatable {
  const CatalogState({
    this.loading = false,
    this.categories = const [],
    this.vendors = const [],
    this.products = const [],
    this.selectedVendor,
    this.error,
  });

  final bool loading;
  final List<Category> categories;
  final List<Vendor> vendors;
  final List<Product> products;
  final Vendor? selectedVendor;
  final String? error;

  CatalogState copyWith({
    bool? loading,
    List<Category>? categories,
    List<Vendor>? vendors,
    List<Product>? products,
    Vendor? selectedVendor,
    String? error,
  }) {
    return CatalogState(
      loading: loading ?? this.loading,
      categories: categories ?? this.categories,
      vendors: vendors ?? this.vendors,
      products: products ?? this.products,
      selectedVendor: selectedVendor ?? this.selectedVendor,
      error: error,
    );
  }

  @override
  List<Object?> get props => [
        loading,
        categories,
        vendors,
        products,
        selectedVendor,
        error,
      ];
}

class CatalogCubit extends Cubit<CatalogState> {
  CatalogCubit(this._repository) : super(const CatalogState());

  final CatalogRepository _repository;

  Future<void> loadHome() async {
    emit(state.copyWith(loading: true));
    try {
      final categories = await _repository.getCategories();
      final vendors = await _repository.getVendors();
      emit(
        state.copyWith(
          loading: false,
          categories: categories,
          vendors: vendors,
        ),
      );
    } catch (_) {
      emit(state.copyWith(loading: false, error: 'Não foi possível carregar.'));
    }
  }

  Future<void> loadStore(String vendorId) async {
    emit(state.copyWith(loading: true));
    try {
      final vendors = state.vendors.isEmpty ? await _repository.getVendors() : state.vendors;
      final vendor = vendors.firstWhere((v) => v.id == vendorId, orElse: () => vendors.first);
      final products = await _repository.getProductsByVendor(vendor.id);
      emit(
        state.copyWith(
          loading: false,
          vendors: vendors,
          selectedVendor: vendor,
          products: products,
        ),
      );
    } catch (_) {
      emit(state.copyWith(loading: false, error: 'Não foi possível carregar a loja.'));
    }
  }
}
