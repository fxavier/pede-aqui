import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../core/network/api_error.dart';
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
      emit(DeliveryState(isLoading: false, delivery: delivery)); // fresh: no OTP/flags from a previous delivery
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

    emit(state.copyWith(isSubmitting: true));
    try {
      await _repository.confirmDelivery(
        deliveryId: delivery.id,
        otpCode: state.otpCode,
        hasProofPhoto: state.hasProofPhoto,
      );

      emit(state.copyWith(
        isSubmitting: false,
        isConfirmed: true,
        delivery: delivery.copyWith(status: DeliveryStatus.delivered),
      ));
    } catch (error) {
      final apiError = ApiError.from(error);
      emit(state.copyWith(
        isSubmitting: false,
        isNotDeliverable: apiError.code == 'order_not_deliverable',
        errorMessage: completionErrorMessage(apiError),
      ));
    }
  }

  /// pt-PT message for a failed `POST /deliveries/{id}/complete`, decided on the problem `code` (ERROR-CONTRACT).
  static String completionErrorMessage(ApiError error) {
    switch (error.code) {
      case 'order_not_deliverable':
        return 'Esta encomenda foi cancelada ou reembolsada e já não pode ser entregue.';
      case 'delivery_code_locked':
        return 'Código bloqueado após demasiadas tentativas erradas. Contacte o suporte.';
      case 'invalid_delivery_code':
        return 'Código inválido. Confirme os 6 dígitos com o cliente.';
    }
    if (error.status == 423) return 'Código bloqueado após demasiadas tentativas erradas. Contacte o suporte.';
    if (error.isRateLimited) {
      final wait = error.retryAfter;
      return wait == null
          ? 'Demasiadas tentativas. Aguarde um momento e tente novamente.'
          : 'Demasiadas tentativas. Aguarde ${wait.inSeconds} s e tente novamente.';
    }
    if (error.isNetwork) return 'Sem ligação à internet. Verifique a sua rede e tente novamente.';
    return 'Não foi possível confirmar a entrega.';
  }
}
