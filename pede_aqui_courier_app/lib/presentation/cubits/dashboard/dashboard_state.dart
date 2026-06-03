import 'package:equatable/equatable.dart';

import '../../../data/models/delivery_models.dart';

class DashboardState extends Equatable {
  const DashboardState({
    required this.isLoading,
    required this.isAvailable,
    this.activeDelivery,
    this.availableJobs = const [],
    this.errorMessage,
  });

  const DashboardState.initial()
      : isLoading = true,
        isAvailable = true,
        activeDelivery = null,
        availableJobs = const [],
        errorMessage = null;

  final bool isLoading;
  final bool isAvailable;
  final Delivery? activeDelivery;
  final List<AvailableJob> availableJobs;
  final String? errorMessage;

  DashboardState copyWith({
    bool? isLoading,
    bool? isAvailable,
    Delivery? activeDelivery,
    List<AvailableJob>? availableJobs,
    String? errorMessage,
  }) {
    return DashboardState(
      isLoading: isLoading ?? this.isLoading,
      isAvailable: isAvailable ?? this.isAvailable,
      activeDelivery: activeDelivery ?? this.activeDelivery,
      availableJobs: availableJobs ?? this.availableJobs,
      errorMessage: errorMessage ?? this.errorMessage,
    );
  }

  @override
  List<Object?> get props => [isLoading, isAvailable, activeDelivery, availableJobs, errorMessage];
}
