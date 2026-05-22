import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../data/repositories/courier_repository.dart';
import 'earnings_state.dart';

class EarningsCubit extends Cubit<EarningsState> {
  EarningsCubit(this._repository) : super(const EarningsState.initial());

  final CourierRepository _repository;

  Future<void> loadEarnings() async {
    emit(state.copyWith(isLoading: true));
    try {
      final summary = await _repository.getEarningSummary();
      final weekly = await _repository.getWeeklyEarnings();
      final history = await _repository.getEarningHistory();
      emit(state.copyWith(isLoading: false, summary: summary, weekly: weekly, history: history));
    } catch (_) {
      emit(state.copyWith(isLoading: false, errorMessage: 'Não foi possível carregar os ganhos.'));
    }
  }
}
