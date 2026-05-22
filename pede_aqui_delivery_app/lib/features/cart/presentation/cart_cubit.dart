import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../data/cart_models.dart';
import '../data/cart_repository.dart';

class CartState extends Equatable {
  const CartState({this.loading = false, this.summary, this.error});

  final bool loading;
  final CartSummary? summary;
  final String? error;

  CartState copyWith({bool? loading, CartSummary? summary, String? error}) {
    return CartState(
      loading: loading ?? this.loading,
      summary: summary ?? this.summary,
      error: error,
    );
  }

  @override
  List<Object?> get props => [loading, summary, error];
}

class CartCubit extends Cubit<CartState> {
  CartCubit(this._repository) : super(const CartState());

  final CartRepository _repository;

  Future<void> loadCart() async {
    emit(state.copyWith(loading: true));
    try {
      final summary = await _repository.getCart();
      emit(state.copyWith(loading: false, summary: summary));
    } catch (_) {
      emit(state.copyWith(loading: false, error: 'Carrinho indisponível.'));
    }
  }

  Future<void> updateQuantity(String productId, int quantity) async {
    final summary = await _repository.updateQuantity(productId, quantity);
    emit(state.copyWith(summary: summary));
  }
}
