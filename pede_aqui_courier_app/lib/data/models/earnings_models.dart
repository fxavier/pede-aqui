import 'package:equatable/equatable.dart';

class EarningSummary extends Equatable {
  const EarningSummary({
    required this.today,
    required this.week,
    required this.month,
    required this.completedDeliveries,
  });

  final int today;
  final int week;
  final int month;
  final int completedDeliveries;

  @override
  List<Object?> get props => [today, week, month, completedDeliveries];
}

class WeeklyEarning extends Equatable {
  const WeeklyEarning({required this.day, required this.amount, this.isToday = false});

  final String day;
  final int amount;
  final bool isToday;

  @override
  List<Object?> get props => [day, amount, isToday];
}

class EarningRecord extends Equatable {
  const EarningRecord({
    required this.id,
    required this.vendorName,
    required this.timeLabel,
    required this.amount,
    required this.status,
    required this.category,
  });

  final String id;
  final String vendorName;
  final String timeLabel;
  final int amount;
  final String status;
  final String category;

  @override
  List<Object?> get props => [id, vendorName, timeLabel, amount, status, category];
}
