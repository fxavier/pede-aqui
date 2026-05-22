import 'package:flutter/material.dart';

import '../../core/constants/app_colors.dart';

class AppLogo extends StatelessWidget {
  const AppLogo({super.key, this.compact = false});

  final bool compact;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: compact ? 34 : 48,
          height: compact ? 34 : 48,
          decoration: BoxDecoration(
            color: AppColors.primary,
            borderRadius: BorderRadius.circular(compact ? 12 : 16),
            boxShadow: [
              BoxShadow(
                color: AppColors.primary.withOpacity(.25),
                blurRadius: 22,
                offset: const Offset(0, 10),
              ),
            ],
          ),
          child: Icon(
            Icons.restaurant_menu_rounded,
            color: Colors.white,
            size: compact ? 18 : 26,
          ),
        ),
        const SizedBox(width: 10),
        Text(
          'Pede Aqui',
          style: TextStyle(
            color: AppColors.primary,
            fontWeight: FontWeight.w900,
            fontSize: compact ? 18 : 24,
            letterSpacing: -.5,
          ),
        ),
      ],
    );
  }
}
