import 'package:flutter/material.dart';

import '../../../app/pede_aqui_app.dart';
import '../../../core/constants/app_colors.dart';
import '../../../features/cart/data/cart_models.dart';
import '../../../shared/widgets/money_text.dart';
import '../../../shared/widgets/primary_button.dart';
import '../../../shared/widgets/screen_shell.dart';

class CheckoutPromotionScreen extends StatelessWidget {
  const CheckoutPromotionScreen({super.key});

  @override
  Widget build(BuildContext context) {
    const summary = CartSummary(
      items: [
        CartItem(productId: 'smokey', name: 'Smokey Jollof Feast', price: 4400, quantity: 1, emoji: '🍛'),
        CartItem(productId: 'suya', name: 'Grilled Beef Suya', price: 2900, quantity: 1, emoji: '🥩'),
      ],
      deliveryFee: 0,
      taxes: 0,
      discount: 1500,
    );

    return ScreenShell(
      padding: const EdgeInsets.fromLTRB(20, 10, 20, 34),
      appBar: AppBar(
        title: const Text('Checkout', style: TextStyle(fontWeight: FontWeight.w900)),
        leading: IconButton(onPressed: () => Navigator.pop(context), icon: const Icon(Icons.arrow_back_rounded)),
        actions: [IconButton(onPressed: () {}, icon: const Icon(Icons.location_on_outlined))],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppColors.green,
              borderRadius: BorderRadius.circular(22),
              boxShadow: [BoxShadow(color: AppColors.green.withOpacity(.25), blurRadius: 24, offset: const Offset(0, 10))],
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: const [
                CircleAvatar(backgroundColor: Colors.white24, child: Icon(Icons.percent_rounded, color: Colors.white)),
                SizedBox(width: 12),
                Expanded(
                  child: Text.rich(
                    TextSpan(
                      text: 'PROMOTION APPLIED\n',
                      style: TextStyle(color: Colors.white70, fontSize: 12, fontWeight: FontWeight.w800),
                      children: [
                        TextSpan(text: 'WEEKENDJOLLOF\n', style: TextStyle(color: Colors.white, fontSize: 17, fontWeight: FontWeight.w900)),
                        TextSpan(text: 'Enjoy free delivery and ₦1,500 off', style: TextStyle(color: Colors.white70, fontWeight: FontWeight.w600)),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 22),
          Row(
            children: [
              const Expanded(child: Text('ENTREGA EM', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w900, color: AppColors.mutedText))),
              TextButton(onPressed: () {}, child: const Text('Change')),
            ],
          ),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(22), border: Border.all(color: AppColors.border)),
            child: Row(
              children: const [
                Icon(Icons.home_rounded, color: AppColors.primary),
                SizedBox(width: 12),
                Expanded(
                  child: Text.rich(
                    TextSpan(
                      text: 'Home (Apt 4B)\n',
                      style: TextStyle(fontWeight: FontWeight.w900),
                      children: [
                        TextSpan(text: '14 Commercial Ave, Ikoyi Suite, Lagos\n', style: TextStyle(fontWeight: FontWeight.w500, color: AppColors.mutedText)),
                        TextSpan(text: '15-35 min', style: TextStyle(fontWeight: FontWeight.w700, color: AppColors.mutedText)),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 22),
          const Text('SEU PEDIDO', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w900, color: AppColors.mutedText)),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(22), border: Border.all(color: AppColors.border)),
            child: Column(
              children: summary.items.map((item) {
                return Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: Row(
                    children: [
                      Container(
                        width: 52,
                        height: 52,
                        decoration: BoxDecoration(color: AppColors.primarySoft, borderRadius: BorderRadius.circular(14)),
                        child: Center(child: Text(item.emoji, style: const TextStyle(fontSize: 25))),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(item.name, style: const TextStyle(fontWeight: FontWeight.w900)),
                            const Text('Regular • Extra Plantain', style: TextStyle(fontSize: 12, color: AppColors.mutedText)),
                          ],
                        ),
                      ),
                      MoneyText(item.price, color: AppColors.text),
                    ],
                  ),
                );
              }).toList(),
            ),
          ),
          const SizedBox(height: 18),
          const Text('PAGAMENTO', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w900, color: AppColors.mutedText)),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(18), border: Border.all(color: AppColors.border)),
            child: Row(children: const [
              Icon(Icons.credit_card_rounded, color: AppColors.blue),
              SizedBox(width: 12),
              Expanded(child: Text('•••• 4242', style: TextStyle(fontWeight: FontWeight.w900))),
              Icon(Icons.chevron_right_rounded),
            ]),
          ),
          const SizedBox(height: 18),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(color: AppColors.surfaceSoft, borderRadius: BorderRadius.circular(22), border: Border.all(color: AppColors.border)),
            child: Column(
              children: [
                _PromoRow(label: 'Subtotal', value: summary.subtotal),
                const _PromoTextRow(label: 'Delivery Fee', value: 'FREE'),
                const _PromoTextRow(label: 'Discount (WEEKENDJOLLOF)', value: '-1,500'),
                const Divider(height: 24),
                Row(children: [
                  const Expanded(child: Text('Total', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900))),
                  MoneyText(summary.total, large: true),
                ]),
              ],
            ),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                  decoration: BoxDecoration(color: AppColors.greenSoft, borderRadius: BorderRadius.circular(12), border: Border.all(color: AppColors.green)),
                  child: const Text('WEEKENDJOLLOF  ✓', style: TextStyle(color: AppColors.green, fontWeight: FontWeight.w900)),
                ),
              ),
              const SizedBox(width: 10),
              OutlinedButton(onPressed: () {}, child: const Text('Remove')),
            ],
          ),
          const SizedBox(height: 22),
          PrimaryButton(
            label: 'Place Order',
            icon: Icons.arrow_forward_rounded,
            onPressed: () => Navigator.pushNamed(context, AppRoutes.orderTracking),
          ),
        ],
      ),
    );
  }
}

class _PromoRow extends StatelessWidget {
  const _PromoRow({required this.label, required this.value});

  final String label;
  final double value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 9),
      child: Row(children: [
        Expanded(child: Text(label, style: const TextStyle(color: AppColors.mutedText))),
        MoneyText(value, color: AppColors.text),
      ]),
    );
  }
}

class _PromoTextRow extends StatelessWidget {
  const _PromoTextRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 9),
      child: Row(children: [
        Expanded(child: Text(label, style: const TextStyle(color: AppColors.mutedText))),
        Text(value, style: const TextStyle(color: AppColors.green, fontWeight: FontWeight.w900)),
      ]),
    );
  }
}
