import 'dart:async';

import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../core/location/location_sender.dart';
import '../../../core/network/api_error.dart';
import '../../../data/models/delivery_models.dart';
import '../../../data/repositories/courier_repository.dart';
import 'dashboard_state.dart';

/// Home dashboard: courier status (online toggle), active delivery and offers. Also owns the "on duty" gate of the
/// [LocationSender]: on duty = status AVAILABLE/BUSY or an active delivery (LAC-40 user decision).
class DashboardCubit extends Cubit<DashboardState> {
  DashboardCubit(this._repository, {LocationSender? sender})
      : _sender = sender,
        super(const DashboardState.initial()) {
    _senderEvents = sender?.events.listen((event) {
      if (event == LocationSenderEvent.offDuty && !isClosed) {
        emit(state.copyWith(isAvailable: false, errorMessage: 'Ficou indisponível. Volte a ligar-se para receber entregas.'));
      }
    });
  }

  final CourierRepository _repository;
  final LocationSender? _sender;
  StreamSubscription<LocationSenderEvent>? _senderEvents;

  static const notDeliverableMessage = 'Esta encomenda foi cancelada ou reembolsada e já não pode ser entregue.';

  Future<void> loadDashboard() async {
    emit(state.copyWith(isLoading: true));
    try {
      final profile = await _repository.getProfile();
      Delivery? activeDelivery;
      try {
        activeDelivery = await _repository.getActiveDelivery();
      } catch (_) {
        activeDelivery = null; // GET /dispatch-jobs empty = no active delivery, not an error
      }
      final jobs = await _repository.getAvailableJobs();
      final status = profile.status;
      emit(DashboardState(
        isLoading: false,
        isAvailable: status == null ? state.isAvailable : (status == 'AVAILABLE' || status == 'BUSY'),
        activeDelivery: activeDelivery,
        availableJobs: jobs,
      ));
      await _syncDuty();
    } catch (_) {
      emit(state.copyWith(isLoading: false, errorMessage: 'Não foi possível carregar o painel.'));
    }
  }

  /// Online/offline toggle (`PUT /couriers/me/status`). The caller makes sure a valid GPS consent exists before
  /// going online. Going offline flushes the GPS buffer first, then stops the sender.
  Future<void> toggleAvailability(bool value) async {
    try {
      if (!value) await _sender?.flush();
      await _repository.updateAvailability(value);
      emit(state.copyWith(isAvailable: value));
      await _syncDuty();
    } catch (error) {
      emit(state.copyWith(errorMessage: _statusError(ApiError.from(error))));
    }
  }

  Future<void> acceptJob(String jobId) async {
    try {
      await _repository.acceptJob(jobId);
      final jobs = state.availableJobs.where((job) => job.id != jobId).toList();
      emit(state.copyWith(availableJobs: jobs));
    } catch (error) {
      if (ApiError.from(error).code == 'order_not_deliverable') {
        removeDelivery(jobId, message: notDeliverableMessage);
      } else {
        emit(state.copyWith(errorMessage: 'Não foi possível aceitar a entrega.'));
      }
    }
  }

  Future<void> rejectJob(String jobId) async {
    await _repository.rejectJob(jobId);
    final jobs = state.availableJobs.where((job) => job.id != jobId).toList();
    emit(state.copyWith(availableJobs: jobs));
  }

  /// Drops a delivery/offer that can no longer be delivered (409 `order_not_deliverable`).
  void removeDelivery(String id, {String? message}) {
    final active = state.activeDelivery;
    emit(DashboardState(
      isLoading: state.isLoading,
      isAvailable: state.isAvailable,
      activeDelivery: active != null && active.id == id ? null : active,
      availableJobs: state.availableJobs.where((job) => job.id != id).toList(),
      errorMessage: message ?? state.errorMessage,
    ));
    _syncDuty();
  }

  Future<void> _syncDuty() async {
    await _sender?.setOnDuty(state.isAvailable || state.activeDelivery != null);
  }

  String _statusError(ApiError error) {
    switch (error.code) {
      case 'courier_has_active_delivery':
        return 'Termine a entrega em curso antes de ficar indisponível.';
      case 'courier_suspended':
        return 'A sua conta está suspensa. Contacte o suporte.';
      case 'courier_not_verified':
        return 'A sua conta ainda não foi aprovada pela equipa Pede Aqui.';
    }
    if (error.isRateLimited) return 'Demasiados pedidos. Aguarde um momento e tente novamente.';
    return 'Não foi possível alterar a disponibilidade.';
  }

  @override
  Future<void> close() async {
    await _senderEvents?.cancel();
    return super.close();
  }
}
