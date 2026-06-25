import 'package:flutter/material.dart';

import '../../core/constants/app_colors.dart';
import '../../core/constants/app_spacing.dart';

/// Pill badge mirrored from the web `Badge`. `green` uses the success tint;
/// otherwise the warm primary tint.
class StatusChip extends StatelessWidget {
  const StatusChip({required this.label, super.key, this.icon, this.color, this.backgroundColor, this.green = true});

  final String label;
  final IconData? icon;
  final Color? color;
  final Color? backgroundColor;
  final bool green;

  @override
  Widget build(BuildContext context) {
    final chipColor = color ?? (green ? AppColors.green : AppColors.primary);
    final chipBg = backgroundColor ?? (green ? AppColors.greenSoft : AppColors.primarySoft);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(color: chipBg, borderRadius: BorderRadius.circular(AppRadii.pill)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 14, color: chipColor),
            const SizedBox(width: 5),
          ],
          Text(
            label,
            style: TextStyle(fontSize: 12, color: chipColor, fontWeight: FontWeight.w700),
          ),
        ],
      ),
    );
  }
}
