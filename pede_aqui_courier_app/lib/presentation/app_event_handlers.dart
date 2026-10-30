import 'dart:async';

import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';

import '../app.dart';
import '../core/location/location_sender.dart';
import '../core/network/api_client.dart';
import 'cubits/location_consent/location_consent_cubit.dart';

/// App-wide reactions that do not belong to one screen: refresh-token failure (W4-E left
/// `ApiClient.onSessionExpired` unconnected) and GPS sender events (LAC-40).
StreamSubscription<LocationSenderEvent> bindAppEventHandlers({
  required ApiClient apiClient,
  required LocationSender sender,
  required LocationConsentCubit consentCubit,
}) {
  apiClient.onSessionExpired = () => _onSessionExpired(sender);
  return sender.events.listen((event) {
    switch (event) {
      case LocationSenderEvent.consentRequired:
        consentCubit.markRequired();
        if (_currentRouteName() != AppRoutes.locationConsent && _currentRouteName() != AppRoutes.login) {
          appNavigatorKey.currentState?.pushNamed(AppRoutes.locationConsent);
        }
      case LocationSenderEvent.permissionDenied:
        appMessengerKey.currentState?.showSnackBar(
          const SnackBar(
            content: Text('Permita o acesso à localização para trabalhar em serviço.'),
            action: SnackBarAction(label: 'Definições', onPressed: Geolocator.openAppSettings),
          ),
        );
      case LocationSenderEvent.offDuty:
        break; // DashboardCubit flips the toggle and explains it.
    }
  });
}

/// The refresh token was rejected: stop GPS, drop the buffer and send the courier back to the login screen.
Future<void> _onSessionExpired(LocationSender sender) async {
  await sender.reset();
  if (_currentRouteName() == AppRoutes.login) return; // the login screen handles its own failures
  appNavigatorKey.currentState?.pushNamedAndRemoveUntil(AppRoutes.login, (_) => false);
  appMessengerKey.currentState?.showSnackBar(
    const SnackBar(content: Text('A sua sessão expirou. Entre novamente.')),
  );
}

String? _currentRouteName() {
  String? name;
  appNavigatorKey.currentState?.popUntil((route) {
    name = route.settings.name;
    return true;
  });
  return name;
}
