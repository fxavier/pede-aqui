/// One GPS fix, in the shape of `POST /couriers/me/locations` points (LAC-40).
class LocationPoint {
  const LocationPoint({
    required this.latitude,
    required this.longitude,
    required this.accuracyMeters,
    required this.recordedAt,
    this.speedMps,
    this.headingDegrees,
  });

  final double latitude;
  final double longitude;
  final double accuracyMeters;
  final double? speedMps;
  final double? headingDegrees;
  final DateTime recordedAt;

  /// Request JSON. Out-of-range optional values are omitted rather than sent (the server answers 400 for them,
  /// which would poison the whole batch): speed must be 0..100 m/s and heading 0..<360.
  Map<String, dynamic> toJson() {
    final speed = speedMps;
    final heading = headingDegrees;
    return {
      'latitude': latitude,
      'longitude': longitude,
      'accuracyMeters': accuracyMeters,
      if (speed != null && speed >= 0 && speed <= 100) 'speedMps': speed,
      if (heading != null && heading >= 0 && heading < 360) 'headingDegrees': heading,
      'recordedAt': recordedAt.toUtc().toIso8601String(),
    };
  }

  factory LocationPoint.fromJson(Map<String, dynamic> json) => LocationPoint(
        latitude: (json['latitude'] as num).toDouble(),
        longitude: (json['longitude'] as num).toDouble(),
        accuracyMeters: (json['accuracyMeters'] as num).toDouble(),
        speedMps: (json['speedMps'] as num?)?.toDouble(),
        headingDegrees: (json['headingDegrees'] as num?)?.toDouble(),
        recordedAt: DateTime.parse(json['recordedAt'] as String),
      );

  /// Coordinates inside the ranges the server validates (a bad fix must not 400 the whole batch).
  bool get isValid =>
      latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180 && accuracyMeters >= 0 && accuracyMeters <= 100000;
}
