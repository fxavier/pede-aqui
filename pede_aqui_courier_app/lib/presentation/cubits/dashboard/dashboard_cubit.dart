import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../data/repositories/courier_repository.dart';
import 'dashboard_state.dart';

class DashboardCubit extends Cubit<DashboardState> {
  DashboardCubit(this._repository) : super(const DashboardState.initial());

  final CourierRepository _repository;

  Future<void> loadDashboard() async {
    emit(state.copyWith(isLoading: true));
    try {
      final activeDelivery = await _repository.getActiveDelivery();
      final jobs = await _repository.getAvailableJobs();
      emit(state.copyWith(isLoading: false, activeDelivery: activeDelivery, availableJobs: jobs));
    } catch (_) {
      emit(state.copyWith(isLoading: false, errorMessage: 'Não foi possível carregar o painel.'));
    }
  }

  Future<void> toggleAvailability(bool value) async {
    try {
      await _repository.updateAvailability(value);
      emit(state.copyWith(isAvailable: value));
    } catch (_) {
      emit(state.copyWith(errorMessage: 'Não foi possível alterar a disponibilidade.'));
    }
  }

  Future<void> acceptJob(String jobId) async {
    await _repository.acceptJob(jobId);
    final jobs = state.availableJobs.where((job) => job.id != jobId).toList();
    emit(state.copyWith(availableJobs: jobs));
  }

  Future<void> rejectJob(String jobId) async {
    await _repository.rejectJob(jobId);
    final jobs = state.availableJobs.where((job) => job.id != jobId).toList();
    emit(state.copyWith(availableJobs: jobs));
  }
}
