import 'package:flutter/material.dart';

import '../../../app/pede_aqui_app.dart';
import '../../../core/constants/app_colors.dart';
import '../../../core/constants/app_shadows.dart';
import '../../../shared/widgets/app_logo.dart';
import '../../../shared/widgets/primary_button.dart';
import '../../../shared/widgets/screen_shell.dart';

class OnboardingScreen extends StatelessWidget {
  const OnboardingScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ScreenShell(
      padding: const EdgeInsets.fromLTRB(26, 20, 26, 32),
      child: Column(
        children: [
          const Align(alignment: Alignment.center, child: AppLogo(compact: true)),
          const SizedBox(height: 54),
          Container(
            height: 270,
            width: double.infinity,
            decoration: BoxDecoration(
              color: AppColors.forest,
              borderRadius: BorderRadius.circular(36),
              boxShadow: AppShadows.warmLg,
            ),
            child: Stack(
              children: [
                Positioned.fill(
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(36),
                    child: const DecoratedBox(
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                          colors: [AppColors.forestDark, AppColors.forest, AppColors.forestLight],
                        ),
                      ),
                    ),
                  ),
                ),
                const Center(child: Icon(Icons.restaurant_rounded, size: 74, color: AppColors.primary)),
                const Positioned(
                  right: 56,
                  bottom: 64,
                  child: _FloatingIcon(icon: Icons.local_cafe_rounded, color: AppColors.green),
                ),
                const Positioned(
                  left: 42,
                  top: 76,
                  child: _FloatingIcon(icon: Icons.fastfood_rounded, color: AppColors.primary),
                ),
              ],
            ),
          ),
          const SizedBox(height: 42),
          const Text(
            'Descubra perto de si',
            textAlign: TextAlign.center,
            style: TextStyle(fontFamily: 'Fraunces', fontSize: 30, fontWeight: FontWeight.w800, letterSpacing: -0.5),
          ),
          const SizedBox(height: 12),
          const Text(
            'Encontre os melhores sabores locais e produtos frescos a poucos passos da sua localização.',
            textAlign: TextAlign.center,
            style: TextStyle(color: AppColors.mutedText, height: 1.45, fontSize: 15),
          ),
          const SizedBox(height: 32),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: const [
              _Dot(active: true),
              _Dot(active: false),
              _Dot(active: false),
            ],
          ),
          const SizedBox(height: 34),
          PrimaryButton(
            label: 'Continuar',
            onPressed: () => Navigator.pushReplacementNamed(context, AppRoutes.auth),
          ),
          const SizedBox(height: 16),
          TextButton(
            onPressed: () => Navigator.pushReplacementNamed(context, AppRoutes.auth),
            child: const Text('Já tenho conta', style: TextStyle(color: AppColors.primary, fontWeight: FontWeight.w800)),
          ),
        ],
      ),
    );
  }
}

class _FloatingIcon extends StatelessWidget {
  const _FloatingIcon({required this.icon, required this.color});

  final IconData icon;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 54,
      height: 54,
      decoration: BoxDecoration(color: color, shape: BoxShape.circle),
      child: Icon(icon, color: Colors.white),
    );
  }
}

class _Dot extends StatelessWidget {
  const _Dot({required this.active});

  final bool active;

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      margin: const EdgeInsets.symmetric(horizontal: 4),
      width: active ? 28 : 7,
      height: 7,
      decoration: BoxDecoration(
        color: active ? AppColors.primary : AppColors.border,
        borderRadius: BorderRadius.circular(999),
      ),
    );
  }
}
