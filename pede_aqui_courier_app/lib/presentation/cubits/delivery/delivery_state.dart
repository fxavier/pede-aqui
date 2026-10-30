import 'package:equatable/equatable.dart';

import '../../../data/models/delivery_models.dart';

class DeliveryState extends Equatable {
  const DeliveryState({
    required this.isLoading,
    this.delivery,
    this.otpCode = '',
    this.hasProofPhoto = false,
    this.isConfirmed = false,
    this.isNotDeliverable = false,
    this.isSubmitting = false,
    this.errorMessage,
  });

  const DeliveryState.initial()
      : isLoading = true,
        delivery = null,
        otpCode = '',
        hasProofPhoto = false,
        isConfirmed = false,
        isNotDeliverable = false,
        isSubmitting = false,
        errorMessage = null;

  final bool isLoading;
  final Delivery? delivery;
  final String otpCode;
  final bool hasProofPhoto;
  final bool isConfirmed;

  /// 409 `order_not_deliverable`: the order was cancelled/refunded; the job must leave the courier's list.
  final bool isNotDeliverable;
  final bool isSubmitting;
  final String? errorMessage;

  bool get canConfirm => otpCode.length == 6 && hasProofPhoto && !isSubmitting && !isNotDeliverable;

  DeliveryState copyWith({
    bool? isLoading,
    Delivery? delivery,
    String? otpCode,
    bool? hasProofPhoto,
    bool? isConfirmed,
    bool? isNotDeliverable,
    bool? isSubmitting,
    String? errorMessage,
  }) {
    return DeliveryState(
      isLoading: isLoading ?? this.isLoading,
      delivery: delivery ?? this.delivery,
      otpCode: otpCode ?? this.otpCode,
      hasProofPhoto: hasProofPhoto ?? this.hasProofPhoto,
      isConfirmed: isConfirmed ?? this.isConfirmed,
      isNotDeliverable: isNotDeliverable ?? this.isNotDeliverable,
      isSubmitting: isSubmitting ?? this.isSubmitting,
      errorMessage: errorMessage,
    );
  }

  @override
  List<Object?> get props => [isLoading, delivery, otpCode, hasProofPhoto, isConfirmed, isNotDeliverable, isSubmitting, errorMessage];
}
