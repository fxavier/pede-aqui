import 'package:flutter/material.dart';

import 'app_colors.dart';

/// Warm, brown-tinted shadows mirrored from the web `shadow-warm*` utilities
/// (rgba(60,30,5,…)). Replaces flat black shadows for brand warmth.
class AppShadows {
  static List<BoxShadow> get warm => [
        BoxShadow(
          color: AppColors.warmShadowBase.withValues(alpha: 0.10),
          blurRadius: 12,
          offset: const Offset(0, 2),
        ),
        BoxShadow(
          color: AppColors.warmShadowBase.withValues(alpha: 0.06),
          blurRadius: 3,
          offset: const Offset(0, 1),
        ),
      ];

  static List<BoxShadow> get warmMd => [
        BoxShadow(
          color: AppColors.warmShadowBase.withValues(alpha: 0.12),
          blurRadius: 24,
          offset: const Offset(0, 8),
        ),
        BoxShadow(
          color: AppColors.warmShadowBase.withValues(alpha: 0.06),
          blurRadius: 6,
          offset: const Offset(0, 2),
        ),
      ];

  static List<BoxShadow> get warmLg => [
        BoxShadow(
          color: AppColors.warmShadowBase.withValues(alpha: 0.16),
          blurRadius: 48,
          offset: const Offset(0, 16),
        ),
      ];
}
