import 'package:equatable/equatable.dart';

import '../../../data/models/courier_profile.dart';

class ProfileState extends Equatable {
  const ProfileState({required this.isLoading, this.profile, this.notifications = const []});

  const ProfileState.initial()
      : isLoading = true,
        profile = null,
        notifications = const [];

  final bool isLoading;
  final CourierProfile? profile;
  final List<NotificationItem> notifications;

  ProfileState copyWith({bool? isLoading, CourierProfile? profile, List<NotificationItem>? notifications}) {
    return ProfileState(
      isLoading: isLoading ?? this.isLoading,
      profile: profile ?? this.profile,
      notifications: notifications ?? this.notifications,
    );
  }

  @override
  List<Object?> get props => [isLoading, profile, notifications];
}
