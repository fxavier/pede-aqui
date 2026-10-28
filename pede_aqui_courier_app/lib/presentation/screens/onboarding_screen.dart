import 'package:flutter/material.dart';

import '../../app.dart';
import '../../core/constants/app_colors.dart';

class OnboardingScreen extends StatelessWidget {
  const OnboardingScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Spacer(),
              Container(
                height: 260,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(32),
                  gradient: const LinearGradient(colors: [AppColors.primary, AppColors.secondary], begin: Alignment.topLeft, end: Alignment.bottomRight),
                ),
                child: const Center(child: Icon(Icons.delivery_dining_rounded, color: Colors.white, size: 140)),
              ),
              const SizedBox(height: 32),
              const Text('Entregas rápidas, ganhos claros.', style: TextStyle(fontSize: 36, height: 1.05, fontWeight: FontWeight.w900, color: AppColors.onSurface)),
              const SizedBox(height: 14),
              const Text('Aceite pedidos próximos, acompanhe rotas em Maputo e confirme entregas com código seguro.', style: TextStyle(color: AppColors.onSurfaceVariant, fontSize: 17, height: 1.4)),
              const Spacer(),
              FilledButton(onPressed: () => Navigator.of(context).pushReplacementNamed(AppRoutes.login), child: const Text('Começar')),
            ],
          ),
        ),
      ),
    );
  }
}
