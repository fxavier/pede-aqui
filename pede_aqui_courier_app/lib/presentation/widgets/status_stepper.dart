import 'package:flutter/material.dart';

import '../../core/constants/app_colors.dart';
import '../../data/models/delivery_models.dart';
import 'app_card.dart';

class DeliveryStatusStepper extends StatelessWidget {
  const DeliveryStatusStepper({super.key, required this.currentStatus});

  final DeliveryStatus currentStatus;

  @override
  Widget build(BuildContext context) {
    final statuses = DeliveryStatus.values;
    final currentIndex = statuses.indexOf(currentStatus);

    return AppCard(
      child: SizedBox(
        height: 118,
        child: Stack(
          alignment: Alignment.topCenter,
          children: [
            Positioned(
              left: 28,
              right: 28,
              top: 25,
              child: Container(height: 2, color: AppColors.outlineVariant.withOpacity(.65)),
            ),
            Positioned(
              left: 28,
              right: 28,
              top: 25,
              child: Align(
                alignment: Alignment.centerLeft,
                child: FractionallySizedBox(
                  widthFactor: currentIndex / (statuses.length - 1),
                  child: Container(height: 2, color: AppColors.primary),
                ),
              ),
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: List.generate(statuses.length, (index) {
                final status = statuses[index];
                final isDone = index < currentIndex;
                final isCurrent = index == currentIndex;
                return Expanded(
                  child: Column(
                    children: [
                      AnimatedContainer(
                        duration: const Duration(milliseconds: 180),
                        width: isCurrent ? 44 : 38,
                        height: isCurrent ? 44 : 38,
                        decoration: BoxDecoration(
                          color: isDone || isCurrent ? AppColors.primary : AppColors.surfaceContainerHighest,
                          shape: BoxShape.circle,
                          border: isCurrent ? Border.all(color: AppColors.primaryFixed, width: 6) : null,
                        ),
                        child: Icon(_iconFor(status, isDone), size: 18, color: isDone || isCurrent ? Colors.white : AppColors.onSurfaceVariant),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        status.shortLabel,
                        textAlign: TextAlign.center,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 11,
                          height: 1.1,
                          fontWeight: FontWeight.w700,
                          color: isCurrent ? AppColors.primary : AppColors.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                );
              }),
            ),
          ],
        ),
      ),
    );
  }

  IconData _iconFor(DeliveryStatus status, bool isDone) {
    if (isDone) return Icons.check_rounded;
    return switch (status) {
      DeliveryStatus.accepted => Icons.check_rounded,
      DeliveryStatus.goingToVendor => Icons.store_mall_directory_outlined,
      DeliveryStatus.atVendor => Icons.store_rounded,
      DeliveryStatus.goingToClient => Icons.delivery_dining_rounded,
      DeliveryStatus.delivered => Icons.home_outlined,
    };
  }
}
