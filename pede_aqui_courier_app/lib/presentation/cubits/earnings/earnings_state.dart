import 'package:equatable/equatable.dart';

import '../../../data/models/earnings_models.dart';

class EarningsState extends Equatable {
  const EarningsState({
    required this.isLoading,
    this.summary,
    this.weekly = const [],
    this.history = const [],
    this.errorMessage,
  });

  const EarningsState.initial()
      : isLoading = true,
        summary = null,
        weekly = const [],
        history = const [],
        errorMessage = null;

  final bool isLoading;
  final EarningSummary? summary;
  final List<WeeklyEarning> weekly;
  final List<EarningRecord> history;
  final String? errorMessage;

  EarningsState copyWith({
    bool? isLoading,
    EarningSummary? summary,
    List<WeeklyEarning>? weekly,
    List<EarningRecord>? history,
    String? errorMessage,
  }) {
    return EarningsState(
      isLoading: isLoading ?? this.isLoading,
      summary: summary ?? this.summary,
      weekly: weekly ?? this.weekly,
      history: history ?? this.history,
      errorMessage: errorMessage,
    );
  }

  @override
  List<Object?> get props => [isLoading, summary, weekly, history, errorMessage];
}
