import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../data/models/delivery_models.dart';
import '../../../data/repositories/courier_repository.dart';
import 'delivery_state.dart';

class DeliveryCubit extends Cubit<DeliveryState> {
  DeliveryCubit(this._repository) : super(const DeliveryState.initial());

  final CourierRepository _repository;

  Future<void> loadActiveDelivery() async {
    emit(state.copyWith(isLoading: true));
    try {
      final delivery = await _repository.getActiveDelivery();
      emit(state.copyWith(isLoading: false, delivery: delivery));
    } catch (_) {
      emit(state.copyWith(isLoading: false, errorMessage: 'Não foi possível carregar a entrega.'));
    }
  }

  void arrivedAtCustomer() {
    final delivery = state.delivery;
    if (delivery == null) return;
    emit(state.copyWith(delivery: delivery.copyWith(status: DeliveryStatus.goingToClient)));
  }

  void updateOtp(String value) {
    final sanitized = value.replaceAll(RegExp(r'[^0-9]'), '');
    emit(state.copyWith(otpCode: sanitized.length > 6 ? sanitized.substring(0, 6) : sanitized));
  }

  void toggleProofPhoto() {
    emit(state.copyWith(hasProofPhoto: !state.hasProofPhoto));
  }

  Future<void> confirmDelivery() async {
    final delivery = state.delivery;
    if (delivery == null || !state.canConfirm) return;

    try {
      await _repository.confirmDelivery(
        deliveryId: delivery.id,
        otpCode: state.otpCode,
        hasProofPhoto: state.hasProofPhoto,
      );

      emit(state.copyWith(
        isConfirmed: true,
        delivery: delivery.copyWith(status: DeliveryStatus.delivered),
      ));
    } catch (_) {
      emit(state.copyWith(errorMessage: 'Não foi possível confirmar a entrega.'));
    }
  }
}
