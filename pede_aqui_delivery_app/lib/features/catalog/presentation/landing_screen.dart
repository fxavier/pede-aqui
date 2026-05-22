import 'package:flutter/material.dart';

import '../../../app/pede_aqui_app.dart';
import '../../../core/constants/app_colors.dart';
import '../../../shared/widgets/app_logo.dart';
import '../../../shared/widgets/primary_button.dart';
import '../../../shared/widgets/screen_shell.dart';
import '../../../shared/widgets/status_chip.dart';

class LandingScreen extends StatelessWidget {
  const LandingScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ScreenShell(
      padding: const EdgeInsets.fromLTRB(24, 20, 24, 32),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Expanded(child: AppLogo(compact: true)),
              SizedBox(
                height: 38,
                child: FilledButton(
                  onPressed: () => Navigator.pushNamed(context, AppRoutes.onboarding),
                  style: FilledButton.styleFrom(backgroundColor: AppColors.primary),
                  child: const Text('Download App', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w800)),
                ),
              ),
            ],
          ),
          const SizedBox(height: 32),
          const StatusChip(label: 'NOVO NA SUA REGIÃO', icon: Icons.auto_awesome_rounded, green: true),
          const SizedBox(height: 18),
          const Text(
            'O seu marketplace de entrega favorito',
            style: TextStyle(fontSize: 34, fontWeight: FontWeight.w900, letterSpacing: -1.1, height: 1.03),
          ),
          const SizedBox(height: 12),
          const Text(
            'Conectando você aos melhores sabores locais com a rapidez que sua fome exige.',
            style: TextStyle(fontSize: 15, color: AppColors.mutedText, height: 1.45),
          ),
          const SizedBox(height: 24),
          _StoreButton(label: 'App Store', icon: Icons.apple_rounded, onTap: () {}),
          const SizedBox(height: 12),
          _StoreButton(label: 'Google Play', icon: Icons.play_arrow_rounded, dark: true, onTap: () {}),
          const SizedBox(height: 30),
          Container(
            height: 300,
            width: double.infinity,
            decoration: BoxDecoration(
              color: const Color(0xFFD9D9D9),
              borderRadius: BorderRadius.circular(18),
            ),
            child: Stack(
              children: [
                const Center(child: Text('img', style: TextStyle(color: AppColors.mutedText))),
                Positioned(
                  left: 18,
                  right: 18,
                  bottom: 18,
                  child: Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(20),
                      boxShadow: [BoxShadow(color: Colors.black.withOpacity(.08), blurRadius: 24, offset: const Offset(0, 10))],
                    ),
                    child: Row(
                      children: [
                        const Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text('Prato do Dia', style: TextStyle(color: AppColors.mutedText, fontWeight: FontWeight.w700)),
                              Text('Moqueca Baiana', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
                            ],
                          ),
                        ),
                        Container(
                          width: 46,
                          height: 46,
                          decoration: const BoxDecoration(color: AppColors.primary, shape: BoxShape.circle),
                          child: const Icon(Icons.shopping_cart_rounded, color: Colors.white),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 22),
          PrimaryButton(
            label: 'Explorar restaurantes',
            icon: Icons.arrow_forward_rounded,
            onPressed: () => Navigator.pushReplacementNamed(context, AppRoutes.home),
          ),
        ],
      ),
    );
  }
}

class _StoreButton extends StatelessWidget {
  const _StoreButton({required this.label, required this.icon, required this.onTap, this.dark = false});

  final String label;
  final IconData icon;
  final VoidCallback onTap;
  final bool dark;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 54,
      child: ElevatedButton.icon(
        onPressed: onTap,
        icon: Icon(icon, color: Colors.white),
        label: Text(label, style: const TextStyle(fontWeight: FontWeight.w900)),
        style: ElevatedButton.styleFrom(
          backgroundColor: dark ? const Color(0xFF1F130F) : AppColors.primary,
          foregroundColor: Colors.white,
          elevation: 0,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        ),
      ),
    );
  }
}
