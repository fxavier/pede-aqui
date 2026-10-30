import 'package:equatable/equatable.dart';

class CourierProfile extends Equatable {
  const CourierProfile({
    required this.id,
    required this.name,
    required this.phone,
    required this.city,
    required this.vehicle,
    required this.rating,
    required this.totalDeliveries,
    required this.completedToday,
    this.status,
  });

  final String id;
  final String name;
  final String phone;
  final String city;
  final String vehicle;
  final double rating;
  final int totalDeliveries;
  final int completedToday;

  /// Operational status from `GET /couriers/me` (W5-B): OFFLINE, AVAILABLE, BUSY or SUSPENDED; null if unknown.
  final String? status;

  @override
  List<Object?> get props => [id, name, phone, city, vehicle, rating, totalDeliveries, completedToday, status];
}

class NotificationItem extends Equatable {
  const NotificationItem({
    required this.id,
    required this.title,
    required this.message,
    required this.timeLabel,
    required this.isRead,
  });

  final String id;
  final String title;
  final String message;
  final String timeLabel;
  final bool isRead;

  @override
  List<Object?> get props => [id, title, message, timeLabel, isRead];
}
