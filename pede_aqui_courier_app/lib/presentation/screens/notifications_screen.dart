import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../core/constants/app_colors.dart';
import '../cubits/profile/profile_cubit.dart';
import '../cubits/profile/profile_state.dart';
import '../widgets/app_card.dart';
import '../widgets/app_top_bar.dart';

class NotificationsScreen extends StatelessWidget {
  const NotificationsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: const AppTopBar(title: 'Notificações', showBack: true),
      body: BlocBuilder<ProfileCubit, ProfileState>(
        builder: (context, state) {
          return ListView(
            padding: const EdgeInsets.fromLTRB(18, 18, 18, 32),
            children: state.notifications
                .map(
                  (item) => Padding(
                    padding: const EdgeInsets.only(bottom: 14),
                    child: AppCard(
                      borderColor: item.isRead ? null : AppColors.primary.withValues(alpha: .25),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          CircleAvatar(
                            backgroundColor: item.isRead ? AppColors.surfaceContainer : AppColors.primaryFixed,
                            child: Icon(item.isRead ? Icons.notifications_none : Icons.notifications_active_outlined, color: AppColors.primary),
                          ),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(item.title, style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 16)),
                                const SizedBox(height: 4),
                                Text(item.message, style: const TextStyle(color: AppColors.onSurfaceVariant, height: 1.35)),
                              ],
                            ),
                          ),
                          Text(item.timeLabel, style: const TextStyle(color: AppColors.primary, fontWeight: FontWeight.w800)),
                        ],
                      ),
                    ),
                  ),
                )
                .toList(),
          );
        },
      ),
    );
  }
}
