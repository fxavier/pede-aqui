import 'package:flutter/material.dart';

import '../../../app/pede_aqui_app.dart';
import '../../../core/constants/app_colors.dart';
import '../../../core/constants/app_shadows.dart';
import '../../../core/constants/app_spacing.dart';
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
        CartItem(productId: 'frango', name: 'Frango Grelhado com Fries', price: 45000, quantity: 1, emoji: '🍗'),
        CartItem(productId: 'pizza', name: 'Pizza Margherita XXL', price: 32000, quantity: 1, emoji: '🍕'),
      ],
      deliveryFee: 0,
      taxes: 0,
      discount: 15000,
    );

    return ScreenShell(
      padding: const EdgeInsets.fromLTRB(20, 10, 20, 34),
      appBar: AppBar(
        title: const Text('Finalizar Encomenda', style: TextStyle(fontFamily: 'Fraunces', fontWeight: FontWeight.w800)),
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
              borderRadius: BorderRadius.circular(AppRadii.card),
              boxShadow: [BoxShadow(color: AppColors.green.withValues(alpha: 0.25), blurRadius: 24, offset: const Offset(0, 10))],
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: const [
                CircleAvatar(backgroundColor: Colors.white24, child: Icon(Icons.percent_rounded, color: Colors.white)),
                SizedBox(width: 12),
                Expanded(
                  child: Text.rich(
                    TextSpan(
                      text: 'PROMOÇÃO APLICADA\n',
                      style: TextStyle(color: Colors.white70, fontSize: 12, fontWeight: FontWeight.w800),
                      children: [
                        TextSpan(text: 'BOASVINDAS10\n', style: TextStyle(color: Colors.white, fontSize: 17, fontWeight: FontWeight.w900)),
                        TextSpan(text: 'Desfrute de entrega grátis e MT 150 de desconto', style: TextStyle(color: Colors.white70, fontWeight: FontWeight.w600)),
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
              TextButton(onPressed: () {}, child: const Text('Alterar')),
            ],
          ),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(AppRadii.card), border: Border.all(color: AppColors.border, width: 0.5), boxShadow: AppShadows.warm),
            child: Row(
              children: const [
                Icon(Icons.home_rounded, color: AppColors.primary),
                SizedBox(width: 12),
                Expanded(
                  child: Text.rich(
                    TextSpan(
                      text: 'Casa (Apt 4B)\n',
                      style: TextStyle(fontWeight: FontWeight.w900),
                      children: [
                        TextSpan(text: 'Av. Julius Nyerere, 123, Polana\n', style: TextStyle(fontWeight: FontWeight.w500, color: AppColors.mutedText)),
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
            decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(AppRadii.card), border: Border.all(color: AppColors.border, width: 0.5), boxShadow: AppShadows.warm),
            child: Column(
              children: summary.items.map((item) {
                return Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: Row(
                    children: [
                      Container(
                        width: 52,
                        height: 52,
                        decoration: BoxDecoration(
                          gradient: const LinearGradient(
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                            colors: [AppColors.surfaceContainer, AppColors.surfaceContainerHigh],
                          ),
                          borderRadius: BorderRadius.circular(AppRadii.md),
                        ),
                        child: Center(child: Text(item.emoji, style: const TextStyle(fontSize: 25))),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(item.name, style: const TextStyle(fontWeight: FontWeight.w900)),
                            const Text('Normal • Extra Porção', style: TextStyle(fontSize: 12, color: AppColors.mutedText)),
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
            decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(AppRadii.lg), border: Border.all(color: AppColors.border, width: 0.5), boxShadow: AppShadows.warm),
            child: Row(children: const [
              Icon(Icons.mobile_friendly_rounded, color: AppColors.blue),
              SizedBox(width: 12),
              Expanded(child: Text('M-Pesa • +258 84 123 4567', style: TextStyle(fontWeight: FontWeight.w900))),
              Icon(Icons.chevron_right_rounded),
            ]),
          ),
          const SizedBox(height: 18),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(color: AppColors.surfaceSoft, borderRadius: BorderRadius.circular(AppRadii.card), border: Border.all(color: AppColors.border, width: 0.5)),
            child: Column(
              children: [
                _PromoRow(label: 'Subtotal', value: summary.subtotal),
                const _PromoTextRow(label: 'Taxa de Entrega', value: 'GRÁTIS'),
                const _PromoTextRow(label: 'Desconto (BOASVINDAS10)', value: '-MT 150,00'),
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
                  child: const Text('BOASVINDAS10  ✓', style: TextStyle(color: AppColors.green, fontWeight: FontWeight.w900)),
                ),
              ),
              const SizedBox(width: 10),
              OutlinedButton(onPressed: () {}, child: const Text('Remover')),
            ],
          ),
          const SizedBox(height: 22),
          PrimaryButton(
            label: 'Fazer Encomenda',
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
