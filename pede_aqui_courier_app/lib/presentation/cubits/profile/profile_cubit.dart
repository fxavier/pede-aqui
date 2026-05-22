import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../data/repositories/courier_repository.dart';
import 'profile_state.dart';

class ProfileCubit extends Cubit<ProfileState> {
  ProfileCubit(this._repository) : super(const ProfileState.initial());

  final CourierRepository _repository;

  Future<void> loadProfile() async {
    emit(state.copyWith(isLoading: true));
    final profile = await _repository.getProfile();
    final notifications = await _repository.getNotifications();
    emit(state.copyWith(isLoading: false, profile: profile, notifications: notifications));
  }
}
