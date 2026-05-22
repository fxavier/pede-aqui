import 'package:flutter/material.dart';

import '../../core/constants/app_colors.dart';

class PedeAquiBottomNavigation extends StatelessWidget {
  const PedeAquiBottomNavigation({super.key, required this.currentIndex, required this.onChanged});

  final int currentIndex;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) {
    final items = const [
      _NavItem(icon: Icons.grid_view_rounded, label: 'Início'),
      _NavItem(icon: Icons.history_rounded, label: 'Histórico'),
      _NavItem(icon: Icons.payments_outlined, label: 'Ganhos'),
      _NavItem(icon: Icons.person_outline, label: 'Perfil'),
    ];

    return SafeArea(
      top: false,
      child: Container(
        margin: const EdgeInsets.fromLTRB(0, 0, 0, 0),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 10),
        decoration: const BoxDecoration(
          color: AppColors.surfaceContainer,
          borderRadius: BorderRadius.vertical(top: Radius.circular(26)),
          boxShadow: [BoxShadow(color: Color(0x1F2C2C3A), blurRadius: 16, offset: Offset(0, -5))],
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: List.generate(items.length, (index) {
            final item = items[index];
            final isSelected = currentIndex == index;
            return GestureDetector(
              onTap: () => onChanged(index),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 180),
                padding: EdgeInsets.symmetric(horizontal: isSelected ? 22 : 12, vertical: 10),
                decoration: BoxDecoration(
                  color: isSelected ? AppColors.secondaryContainer : Colors.transparent,
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(item.icon, color: isSelected ? AppColors.secondary : AppColors.onSurfaceVariant),
                    const SizedBox(height: 2),
                    Text(
                      item.label,
                      style: TextStyle(
                        color: isSelected ? AppColors.secondary : AppColors.onSurfaceVariant,
                        fontSize: 12,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ],
                ),
              ),
            );
          }),
        ),
      ),
    );
  }
}

class _NavItem {
  const _NavItem({required this.icon, required this.label});
  final IconData icon;
  final String label;
}
