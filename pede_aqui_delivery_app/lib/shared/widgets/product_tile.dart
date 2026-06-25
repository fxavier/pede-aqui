import 'package:flutter/material.dart';

import '../../core/constants/app_colors.dart';
import '../../core/constants/app_shadows.dart';
import '../../core/constants/app_spacing.dart';
import '../../features/catalog/data/catalog_models.dart';
import 'money_text.dart';
import 'status_chip.dart';

/// Product card mirrored from the web `ProductCard`: emoji thumbnail on a warm
/// secondary gradient, name + description, primary-coloured price, and a
/// forest "Comprar" CTA.
class ProductTile extends StatelessWidget {
  const ProductTile({required this.product, super.key, this.onAdd});

  final Product product;
  final VoidCallback? onAdd;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(AppRadii.card),
        border: Border.all(color: AppColors.border, width: 0.5),
        boxShadow: AppShadows.warm,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Container(
              width: double.infinity,
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [AppColors.surfaceContainer, AppColors.surfaceContainerHigh],
                ),
                borderRadius: BorderRadius.circular(AppRadii.md),
              ),
              child: Center(child: Text(product.emoji, style: const TextStyle(fontSize: 40))),
            ),
          ),
          const SizedBox(height: 10),
          if (product.badge != null) ...[
            StatusChip(label: product.badge!),
            const SizedBox(height: 6),
          ],
          Text(product.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
          const SizedBox(height: 2),
          Text(
            product.description,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 12, color: AppColors.mutedText),
          ),
          const SizedBox(height: 6),
          MoneyText(product.price),
          const SizedBox(height: 8),
          _ComprarButton(onTap: onAdd),
        ],
      ),
    );
  }
}

/// Forest "Comprar" CTA — mirrors the web idle-state add-to-cart button.
class _ComprarButton extends StatelessWidget {
  const _ComprarButton({this.onTap});

  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 40,
      child: FilledButton.icon(
        onPressed: onTap,
        style: FilledButton.styleFrom(
          backgroundColor: AppColors.forest,
          foregroundColor: Colors.white,
          padding: EdgeInsets.zero,
          minimumSize: const Size.fromHeight(40),
          textStyle: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700, fontFamily: 'Plus Jakarta Sans'),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadii.md)),
        ),
        icon: const Icon(Icons.shopping_cart_rounded, size: 16),
        label: const Text('Comprar'),
      ),
    );
  }
}
