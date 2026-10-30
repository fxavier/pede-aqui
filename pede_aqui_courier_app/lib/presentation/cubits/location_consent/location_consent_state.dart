import 'package:equatable/equatable.dart';

import '../../../data/models/location_consent.dart';

class LocationConsentState extends Equatable {
  const LocationConsentState({this.isLoading = false, this.isSubmitting = false, this.consent, this.errorMessage});

  final bool isLoading;
  final bool isSubmitting;

  /// Null until the first successful `GET /couriers/me/location-consent`.
  final LocationConsent? consent;
  final String? errorMessage;

  bool get isLoaded => consent != null;
  bool get isValid => consent?.valid ?? false;

  LocationConsentState copyWith({bool? isLoading, bool? isSubmitting, LocationConsent? consent, String? errorMessage}) {
    return LocationConsentState(
      isLoading: isLoading ?? this.isLoading,
      isSubmitting: isSubmitting ?? this.isSubmitting,
      consent: consent ?? this.consent,
      errorMessage: errorMessage,
    );
  }

  @override
  List<Object?> get props => [isLoading, isSubmitting, consent, errorMessage];
}
