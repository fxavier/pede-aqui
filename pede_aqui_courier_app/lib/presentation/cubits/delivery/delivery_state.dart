import 'package:equatable/equatable.dart';

import '../../../data/models/delivery_models.dart';

class DeliveryState extends Equatable {
  const DeliveryState({
    required this.isLoading,
    this.delivery,
    this.otpCode = '',
    this.hasProofPhoto = false,
    this.isConfirmed = false,
    this.errorMessage,
  });

  const DeliveryState.initial()
      : isLoading = true,
        delivery = null,
        otpCode = '',
        hasProofPhoto = false,
        isConfirmed = false,
        errorMessage = null;

  final bool isLoading;
  final Delivery? delivery;
  final String otpCode;
  final bool hasProofPhoto;
  final bool isConfirmed;
  final String? errorMessage;

  bool get canConfirm => otpCode.length == 6 && hasProofPhoto;

  DeliveryState copyWith({
    bool? isLoading,
    Delivery? delivery,
    String? otpCode,
    bool? hasProofPhoto,
    bool? isConfirmed,
    String? errorMessage,
  }) {
    return DeliveryState(
      isLoading: isLoading ?? this.isLoading,
      delivery: delivery ?? this.delivery,
      otpCode: otpCode ?? this.otpCode,
      hasProofPhoto: hasProofPhoto ?? this.hasProofPhoto,
      isConfirmed: isConfirmed ?? this.isConfirmed,
      errorMessage: errorMessage,
    );
  }

  @override
  List<Object?> get props => [isLoading, delivery, otpCode, hasProofPhoto, isConfirmed, errorMessage];
}
