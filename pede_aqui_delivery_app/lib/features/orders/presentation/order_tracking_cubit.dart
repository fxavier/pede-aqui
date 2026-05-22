import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../data/order_models.dart';
import '../data/order_repository.dart';

class OrderTrackingState extends Equatable {
  const OrderTrackingState({this.loading = false, this.order, this.error});

  final bool loading;
  final DeliveryOrder? order;
  final String? error;

  OrderTrackingState copyWith({bool? loading, DeliveryOrder? order, String? error}) {
    return OrderTrackingState(
      loading: loading ?? this.loading,
      order: order ?? this.order,
      error: error,
    );
  }

  @override
  List<Object?> get props => [loading, order, error];
}

class OrderTrackingCubit extends Cubit<OrderTrackingState> {
  OrderTrackingCubit(this._repository) : super(const OrderTrackingState());

  final OrderRepository _repository;

  Future<void> loadActiveOrder() async {
    emit(state.copyWith(loading: true));
    try {
      final order = await _repository.getActiveOrder();
      emit(state.copyWith(loading: false, order: order));
    } catch (_) {
      emit(state.copyWith(loading: false, error: 'Não foi possível carregar a encomenda.'));
    }
  }
}
