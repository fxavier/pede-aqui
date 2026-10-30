import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:geolocator/geolocator.dart';

import 'location_point.dart';

/// Outcome of asking the OS for location access.
enum LocationAccess { granted, denied, deniedForever, serviceDisabled }

/// Where GPS fixes come from. The real one wraps `geolocator`; tests and USE_MOCK_DATA use fakes.
abstract interface class LocationSource {
  /// Checks (and if needed requests) the "while in use" permission and that location services are on.
  Future<LocationAccess> ensureAccess();

  /// Starts emitting fixes. On Android this also starts the location foreground service (persistent notification).
  Stream<LocationPoint> positions();
}

/// `geolocator` with a foreground service on Android and background-capable updates on iOS, so tracking keeps going
/// while the screen is off during a shift. Only "while in use" permission is requested — no background location.
class GeolocatorLocationSource implements LocationSource {
  @override
  Future<LocationAccess> ensureAccess() async {
    if (!await Geolocator.isLocationServiceEnabled()) return LocationAccess.serviceDisabled;
    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }
    switch (permission) {
      case LocationPermission.whileInUse:
      case LocationPermission.always:
        return LocationAccess.granted;
      case LocationPermission.deniedForever:
        return LocationAccess.deniedForever;
      case LocationPermission.denied:
      case LocationPermission.unableToDetermine:
        return LocationAccess.denied;
    }
  }

  @override
  Stream<LocationPoint> positions() {
    return Geolocator.getPositionStream(locationSettings: _settings()).map(
          (p) => LocationPoint(
            latitude: p.latitude,
            longitude: p.longitude,
            accuracyMeters: p.accuracy,
            speedMps: p.speed >= 0 ? p.speed : null,
            headingDegrees: p.heading >= 0 ? p.heading : null,
            recordedAt: p.timestamp.toUtc(),
          ),
        );
  }

  LocationSettings _settings() {
    const distanceFilter = 15; // metres: a parked courier produces no fixes, which saves battery and uploads
    if (defaultTargetPlatform == TargetPlatform.android) {
      return AndroidSettings(
        accuracy: LocationAccuracy.high,
        distanceFilter: distanceFilter,
        intervalDuration: const Duration(seconds: 5),
        foregroundNotificationConfig: const ForegroundNotificationConfig(
          notificationTitle: 'Pede Aqui Estafeta — em serviço',
          notificationText: 'A partilhar a sua localização enquanto está em serviço.',
          notificationChannelName: 'Localização em serviço',
          setOngoing: true,
        ),
      );
    }
    if (defaultTargetPlatform == TargetPlatform.iOS) {
      return AppleSettings(
        accuracy: LocationAccuracy.high,
        distanceFilter: distanceFilter,
        activityType: ActivityType.automotiveNavigation,
        pauseLocationUpdatesAutomatically: false,
        allowBackgroundLocationUpdates: true,
        showBackgroundLocationIndicator: true,
      );
    }
    return const LocationSettings(accuracy: LocationAccuracy.high, distanceFilter: distanceFilter);
  }
}

/// USE_MOCK_DATA source: a slow walk around Polana (Maputo), one fix every [interval]. No platform channels.
class SimulatedLocationSource implements LocationSource {
  SimulatedLocationSource({this.interval = const Duration(seconds: 5)});

  final Duration interval;

  @override
  Future<LocationAccess> ensureAccess() async => LocationAccess.granted;

  @override
  Stream<LocationPoint> positions() {
    var step = 0;
    return Stream<LocationPoint>.periodic(interval, (_) {
      step++;
      final angle = step / 20;
      return LocationPoint(
        latitude: -25.9655 + 0.002 * math.sin(angle),
        longitude: 32.5832 + 0.002 * math.cos(angle),
        accuracyMeters: 8,
        speedMps: 4,
        recordedAt: DateTime.now().toUtc(),
      );
    });
  }
}
