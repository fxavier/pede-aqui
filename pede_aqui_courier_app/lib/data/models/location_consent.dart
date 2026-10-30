import 'package:equatable/equatable.dart';

/// `GET /couriers/me/location-consent`: the text version to show, what was accepted and whether it is current.
class LocationConsent extends Equatable {
  const LocationConsent({required this.currentVersion, this.acceptedVersion, this.acceptedAt, required this.valid});

  final String currentVersion;
  final String? acceptedVersion;
  final DateTime? acceptedAt;
  final bool valid;

  factory LocationConsent.fromJson(Map<String, dynamic> json) => LocationConsent(
        currentVersion: json['currentVersion'] as String,
        acceptedVersion: json['acceptedVersion'] as String?,
        acceptedAt: json['acceptedAt'] == null ? null : DateTime.parse(json['acceptedAt'] as String),
        valid: json['valid'] as bool? ?? false,
      );

  @override
  List<Object?> get props => [currentVersion, acceptedVersion, acceptedAt, valid];
}

/// `POST /couriers/me/locations` outcome.
class LocationBatchResult {
  const LocationBatchResult({required this.accepted, required this.dropped});

  final int accepted;
  final int dropped;
}
