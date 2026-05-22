import 'package:flutter/material.dart';

import '../../core/constants/app_colors.dart';

class StatusChip extends StatelessWidget {
  const StatusChip({required this.label, super.key, this.icon, this.color, this.backgroundColor, this.green = true});

  final String label;
  final IconData? icon;
  final Color? color;
  final Color? backgroundColor;
  final bool green;

  @override
  Widget build(BuildContext context) {
    final chipColor = color ?? (green ? AppColors.secondary : AppColors.primary);
    final chipBg = backgroundColor ?? (green ? AppColors.secondaryContainer : AppColors.primaryContainer);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
      decoration: BoxDecoration(color: chipBg, borderRadius: BorderRadius.circular(999)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 14, color: chipColor),
            const SizedBox(width: 5),
          ],
          Text(
            label,
            style: TextStyle(fontSize: 12, color: chipColor, fontWeight: FontWeight.w800),
          ),
        ],
      ),
    );
  }
}
