import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../core/location/location_sender.dart';
import '../../../core/network/api_error.dart';
import '../../../data/datasources/location_data_source.dart';
import '../../../data/models/location_consent.dart';
import 'location_consent_state.dart';

/// GPS consent (LAC-40): loads the version to show, records acceptance and withdrawal, and keeps the
/// [LocationSender] consent gate in sync.
class LocationConsentCubit extends Cubit<LocationConsentState> {
  LocationConsentCubit(this._api, this._sender) : super(const LocationConsentState());

  final LocationDataSource _api;
  final LocationSender? _sender;

  /// `GET /couriers/me/location-consent`.
  Future<void> load() async {
    emit(state.copyWith(isLoading: true));
    try {
      final consent = await _api.getConsent();
      emit(state.copyWith(isLoading: false, consent: consent));
      await _sender?.setConsent(consent.valid);
    } catch (_) {
      emit(state.copyWith(isLoading: false, errorMessage: 'Não foi possível verificar o consentimento de localização.'));
    }
  }

  /// Accepts the version currently displayed. Returns true on success.
  Future<bool> accept() async {
    final version = state.consent?.currentVersion;
    if (version == null) return false;
    emit(state.copyWith(isSubmitting: true));
    try {
      final consent = await _api.acceptConsent(version);
      emit(state.copyWith(isSubmitting: false, consent: consent));
      await _sender?.setConsent(consent.valid);
      return consent.valid;
    } catch (error) {
      final apiError = ApiError.from(error);
      if (apiError.code == 'location_consent_outdated') {
        // The text changed while the screen was open: show the new version and ask again.
        await load();
        emit(state.copyWith(
          isSubmitting: false,
          errorMessage: 'O texto do consentimento foi actualizado. Leia a nova versão e aceite novamente.',
        ));
      } else {
        emit(state.copyWith(isSubmitting: false, errorMessage: 'Não foi possível registar o consentimento. Tente novamente.'));
      }
      return false;
    }
  }

  /// Withdraws the consent (`DELETE`). The server takes the courier OFFLINE; refused mid-delivery. Returns true on success.
  Future<bool> revoke() async {
    emit(state.copyWith(isSubmitting: true));
    try {
      await _api.withdrawConsent();
      final current = state.consent;
      emit(state.copyWith(
        isSubmitting: false,
        consent: current == null
            ? null
            : LocationConsent(currentVersion: current.currentVersion, valid: false),
      ));
      await _sender?.setConsent(false);
      await _sender?.setOnDuty(false);
      return true;
    } catch (error) {
      final apiError = ApiError.from(error);
      emit(state.copyWith(
        isSubmitting: false,
        errorMessage: apiError.code == 'courier_has_active_delivery'
            ? 'Termine a entrega em curso antes de retirar o consentimento de localização.'
            : 'Não foi possível retirar o consentimento. Tente novamente.',
      ));
      return false;
    }
  }

  /// The server answered `location_consent_required` (e.g. the text version was bumped).
  void markRequired() {
    final current = state.consent;
    if (current != null && current.valid) {
      emit(state.copyWith(consent: LocationConsent(currentVersion: current.currentVersion, valid: false)));
    }
  }
}
