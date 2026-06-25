import 'package:flutter/material.dart';

import '../../core/constants/app_colors.dart';
import '../../core/constants/app_shadows.dart';

/// Brand wordmark mirrored from the web header: scooter glyph + "Pede Aqui"
/// in Fraunces. `onDark` renders the white-on-forest variant.
class AppLogo extends StatelessWidget {
  const AppLogo({super.key, this.compact = false, this.onDark = false});

  final bool compact;
  final bool onDark;

  @override
  Widget build(BuildContext context) {
    final wordColor = onDark ? Colors.white : AppColors.forest;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: compact ? 34 : 46,
          height: compact ? 34 : 46,
          decoration: BoxDecoration(
            color: AppColors.forest,
            borderRadius: BorderRadius.circular(compact ? 12 : 14),
            boxShadow: AppShadows.warm,
          ),
          child: Icon(
            Icons.delivery_dining_rounded,
            color: Colors.white,
            size: compact ? 18 : 26,
          ),
        ),
        const SizedBox(width: 10),
        Text(
          'Pede Aqui',
          style: TextStyle(
            fontFamily: 'Fraunces',
            color: wordColor,
            fontWeight: FontWeight.w800,
            fontSize: compact ? 18 : 24,
            letterSpacing: -0.5,
          ),
        ),
      ],
    );
  }
}
