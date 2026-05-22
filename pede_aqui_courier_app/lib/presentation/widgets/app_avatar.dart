import 'package:flutter/material.dart';

import '../../core/constants/app_colors.dart';

class AppAvatar extends StatelessWidget {
  const AppAvatar({super.key, this.size = 44, this.initials = 'FM'});

  final double size;
  final String initials;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFFFFB4A2), AppColors.primary],
        ),
        border: Border.all(color: AppColors.primaryFixed, width: 2),
      ),
      alignment: Alignment.center,
      child: Text(
        initials,
        style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w900),
      ),
    );
  }
}
