import 'package:flutter/material.dart';

import '../../core/constants/app_colors.dart';
import '../../features/cart/data/cart_models.dart';
import 'money_text.dart';

class OrderSummaryCard extends StatelessWidget {
  const OrderSummaryCard({required this.summary, super.key, this.compact = false});

  final CartSummary summary;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: AppColors.border),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(.04),
            blurRadius: 20,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Resumo do Pedido', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w900)),
          const SizedBox(height: 14),
          _Row(label: 'Subtotal', value: summary.subtotal),
          _Row(label: 'Taxa de Entrega', value: summary.deliveryFee),
          _Row(label: 'Impostos', value: summary.taxes),
          if (summary.discount > 0) _Row(label: 'Desconto', value: -summary.discount, green: true),
          const Divider(height: 26),
          Row(
            children: [
              const Expanded(
                child: Text('Total', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
              ),
              MoneyText(summary.total, large: !compact),
            ],
          ),
        ],
      ),
    );
  }
}

class _Row extends StatelessWidget {
  const _Row({required this.label, required this.value, this.green = false});

  final String label;
  final double value;
  final bool green;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        children: [
          Expanded(
            child: Text(label, style: const TextStyle(color: AppColors.mutedText, fontWeight: FontWeight.w600)),
          ),
          MoneyText(value, color: green ? AppColors.green : AppColors.text),
        ],
      ),
    );
  }
}
