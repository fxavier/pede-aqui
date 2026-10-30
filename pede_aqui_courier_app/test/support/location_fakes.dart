import 'dart:async';

import 'package:dio/dio.dart';
import 'package:pede_aqui_courier_app/core/location/location_point.dart';
import 'package:pede_aqui_courier_app/core/location/location_source.dart';
import 'package:pede_aqui_courier_app/data/datasources/location_data_source.dart';
import 'package:pede_aqui_courier_app/data/models/location_consent.dart';

/// Stands in for geolocator: tests push fixes with [emit].
class FakeLocationSource implements LocationSource {
  LocationAccess access = LocationAccess.granted;
  int accessChecks = 0;
  StreamController<LocationPoint>? _controller;

  bool get isListening => _controller?.hasListener ?? false;

  @override
  Future<LocationAccess> ensureAccess() async {
    accessChecks++;
    return access;
  }

  @override
  Stream<LocationPoint> positions() {
    final controller = StreamController<LocationPoint>();
    _controller = controller;
    return controller.stream;
  }

  void emit(LocationPoint point) => _controller!.add(point);
}

/// Records batches and consent calls; [errors] are thrown (FIFO) by the next calls to [sendBatch].
class FakeLocationApi implements LocationDataSource {
  final List<List<LocationPoint>> batches = [];
  final List<Object> errors = [];
  final List<String> acceptedVersions = [];
  int withdrawals = 0;
  int consentReads = 0;
  String currentVersion = '2026-10-v1';
  bool valid = false;
  Object? acceptError;
  Object? withdrawError;

  @override
  Future<LocationBatchResult> sendBatch(List<LocationPoint> points) async {
    if (errors.isNotEmpty) throw errors.removeAt(0);
    batches.add(List.of(points));
    return LocationBatchResult(accepted: points.length, dropped: 0);
  }

  @override
  Future<LocationConsent> getConsent() async {
    consentReads++;
    return LocationConsent(currentVersion: currentVersion, acceptedVersion: valid ? currentVersion : null, valid: valid);
  }

  @override
  Future<LocationConsent> acceptConsent(String version) async {
    final error = acceptError;
    if (error != null) {
      acceptError = null;
      throw error;
    }
    acceptedVersions.add(version);
    valid = version == currentVersion;
    return LocationConsent(currentVersion: currentVersion, acceptedVersion: version, valid: valid);
  }

  @override
  Future<void> withdrawConsent() async {
    final error = withdrawError;
    if (error != null) throw error;
    withdrawals++;
    valid = false;
  }
}

LocationPoint point(DateTime at, {double lat = -25.96, double lng = 32.58, double accuracy = 10}) =>
    LocationPoint(latitude: lat, longitude: lng, accuracyMeters: accuracy, recordedAt: at);

/// A DioException carrying an RFC 9457 problem body, as the backend sends it.
DioException problem(int status, String code, {Map<String, List<String>> headers = const {}}) {
  final options = RequestOptions(path: '/couriers/me/locations');
  return DioException(
    requestOptions: options,
    type: DioExceptionType.badResponse,
    response: Response<Map<String, dynamic>>(
      requestOptions: options,
      statusCode: status,
      data: {'status': status, 'code': code, 'detail': code},
      headers: Headers.fromMap(headers),
    ),
  );
}

/// No HTTP response at all (offline).
DioException offline() => DioException(
      requestOptions: RequestOptions(path: '/couriers/me/locations'),
      type: DioExceptionType.connectionError,
    );
