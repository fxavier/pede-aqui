import 'package:flutter/material.dart';

import '../../core/constants/app_colors.dart';
import '../../features/catalog/data/catalog_models.dart';
import 'money_text.dart';
import 'status_chip.dart';

class ProductTile extends StatelessWidget {
  const ProductTile({required this.product, super.key, this.onAdd});

  final Product product;
  final VoidCallback? onAdd;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Container(
              width: double.infinity,
              decoration: BoxDecoration(
                color: AppColors.primarySoft,
                borderRadius: BorderRadius.circular(18),
              ),
              child: Center(child: Text(product.emoji, style: const TextStyle(fontSize: 42))),
            ),
          ),
          const SizedBox(height: 10),
          if (product.badge != null) ...[
            StatusChip(label: product.badge!, green: true),
            const SizedBox(height: 6),
          ],
          Text(product.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w900)),
          const SizedBox(height: 4),
          Text(
            product.description,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 12, color: AppColors.mutedText),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(child: MoneyText(product.price)),
              SizedBox(
                width: 34,
                height: 34,
                child: IconButton.filled(
                  onPressed: onAdd,
                  style: IconButton.styleFrom(backgroundColor: AppColors.primary),
                  icon: const Icon(Icons.add_rounded, size: 18),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
