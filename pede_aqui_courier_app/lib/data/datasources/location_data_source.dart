import '../../core/location/location_point.dart';
import '../../core/network/api_client.dart';
import '../models/location_consent.dart';

/// Courier GPS endpoints (LAC-40, W5-B): consent and location batches. Errors surface as the raw Dio/Api errors;
/// callers map them with `ApiError.from`.
abstract interface class LocationDataSource {
  /// `GET /couriers/me/location-consent`.
  Future<LocationConsent> getConsent();

  /// `PUT /couriers/me/location-consent` with the version shown to the courier (409 `location_consent_outdated`).
  Future<LocationConsent> acceptConsent(String version);

  /// `DELETE /couriers/me/location-consent` — the courier goes OFFLINE (409 `courier_has_active_delivery`).
  Future<void> withdrawConsent();

  /// `POST /couriers/me/locations` with 1..50 points.
  Future<LocationBatchResult> sendBatch(List<LocationPoint> points);
}

class RemoteLocationDataSource implements LocationDataSource {
  RemoteLocationDataSource(this._api);

  final ApiClient _api;

  @override
  Future<LocationConsent> getConsent() async {
    final response = await _api.get<Map<String, dynamic>>('/couriers/me/location-consent');
    return LocationConsent.fromJson(response.data!);
  }

  @override
  Future<LocationConsent> acceptConsent(String version) async {
    final response = await _api.put<Map<String, dynamic>>('/couriers/me/location-consent', data: {'version': version});
    return LocationConsent.fromJson(response.data!);
  }

  @override
  Future<void> withdrawConsent() => _api.delete<void>('/couriers/me/location-consent');

  @override
  Future<LocationBatchResult> sendBatch(List<LocationPoint> points) async {
    final response = await _api.post<Map<String, dynamic>>(
      '/couriers/me/locations',
      data: {'points': points.map((p) => p.toJson()).toList()},
    );
    final data = response.data ?? const {};
    return LocationBatchResult(
      accepted: (data['accepted'] as num?)?.toInt() ?? 0,
      dropped: (data['dropped'] as num?)?.toInt() ?? 0,
    );
  }
}

/// USE_MOCK_DATA: consent held in memory, every batch accepted.
class MockLocationDataSource implements LocationDataSource {
  static const currentVersion = '2026-10-v1';
  String? _accepted;
  DateTime? _acceptedAt;

  @override
  Future<LocationConsent> getConsent() async => _state();

  @override
  Future<LocationConsent> acceptConsent(String version) async {
    _accepted = version;
    _acceptedAt = DateTime.now();
    return _state();
  }

  @override
  Future<void> withdrawConsent() async {
    _accepted = null;
    _acceptedAt = null;
  }

  @override
  Future<LocationBatchResult> sendBatch(List<LocationPoint> points) async =>
      LocationBatchResult(accepted: points.length, dropped: 0);

  LocationConsent _state() => LocationConsent(
        currentVersion: currentVersion,
        acceptedVersion: _accepted,
        acceptedAt: _acceptedAt,
        valid: _accepted == currentVersion,
      );
}
