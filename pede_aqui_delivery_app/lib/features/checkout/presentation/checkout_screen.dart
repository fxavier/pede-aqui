import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../app/pede_aqui_app.dart';
import '../../../core/constants/app_colors.dart';
import '../../../core/constants/app_shadows.dart';
import '../../../core/constants/app_spacing.dart';
import '../../../features/cart/presentation/cart_cubit.dart';
import '../../../shared/widgets/money_text.dart';
import '../../../shared/widgets/primary_button.dart';
import '../../../shared/widgets/screen_shell.dart';

class CheckoutScreen extends StatelessWidget {
  const CheckoutScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final summary = context.watch<CartCubit>().state.summary;

    return ScreenShell(
      padding: const EdgeInsets.fromLTRB(20, 10, 20, 34),
      appBar: AppBar(
        title: const Text('Finalizar\nEncomenda', style: TextStyle(fontFamily: 'Fraunces', fontWeight: FontWeight.w800, height: 1.0)),
        leading: IconButton(onPressed: () => Navigator.pop(context), icon: const Icon(Icons.arrow_back_rounded)),
        actions: const [
          Padding(
            padding: EdgeInsets.only(right: 14),
            child: Row(
              children: [
                Icon(Icons.location_on_outlined, size: 18, color: AppColors.primary),
                SizedBox(width: 4),
                Text('Polana\nMaputo', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w800)),
              ],
            ),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(color: AppColors.greenSoft, borderRadius: BorderRadius.circular(AppRadii.card)),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: const [
                Icon(Icons.verified_rounded, color: AppColors.green),
                SizedBox(width: 12),
                Expanded(
                  child: Text.rich(
                    TextSpan(
                      text: 'Receita Validada\n',
                      style: TextStyle(fontWeight: FontWeight.w900),
                      children: [
                        TextSpan(
                          text: 'A sua receita médica foi verificada com sucesso pela farmácia parceira.',
                          style: TextStyle(fontWeight: FontWeight.w500, color: AppColors.mutedText, height: 1.35),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 22),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(AppRadii.card), border: Border.all(color: AppColors.border, width: 0.5), boxShadow: AppShadows.warm),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Expanded(child: Text('Resumo do Pedido', style: TextStyle(fontFamily: 'Fraunces', fontSize: 16, fontWeight: FontWeight.w800))),
                    TextButton(onPressed: () => Navigator.pushNamed(context, AppRoutes.cart), child: const Text('Editar')),
                  ],
                ),
                const SizedBox(height: 10),
                _CheckoutLine(emoji: '💊', title: 'Amoxicilina 500mg', subtitle: 'Qtd: 1', value: 450),
                const SizedBox(height: 10),
                _CheckoutLine(emoji: '🍊', title: 'Vitamina C + Zinco', subtitle: 'Qtd: 2', value: 320),
                const Divider(height: 26),
                _AmountRow(label: 'Subtotal', value: summary?.subtotal ?? 1090),
                _AmountRow(label: 'Taxa de Entrega', value: summary?.deliveryFee ?? 120),
                _AmountRow(label: 'Total', value: summary?.total ?? 1210, strong: true),
              ],
            ),
          ),
          const SizedBox(height: 24),
          const Text('Método de Pagamento', style: TextStyle(fontFamily: 'Fraunces', fontSize: 20, fontWeight: FontWeight.w800)),
          const SizedBox(height: 14),
          const _PaymentOption(
            selected: true,
            icon: Icons.phone_android_rounded,
            title: 'M-Pesa',
            subtitle: 'Pagamento móvel instantâneo',
          ),
          const SizedBox(height: 12),
          const _PaymentOption(
            selected: false,
            icon: Icons.payments_outlined,
            title: 'Dinheiro',
            subtitle: 'Pagar no acto da entrega',
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              const Expanded(child: TextField(decoration: InputDecoration(hintText: 'Código Promocional'))),
              const SizedBox(width: 10),
              OutlinedButton(
                onPressed: () => Navigator.pushReplacementNamed(context, AppRoutes.checkoutPromo),
                style: OutlinedButton.styleFrom(
                  minimumSize: const Size(86, 54),
                  side: const BorderSide(color: AppColors.green),
                  foregroundColor: AppColors.green,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadii.base)),
                ),
                child: const Text('Aplicar', style: TextStyle(fontWeight: FontWeight.w900)),
              ),
            ],
          ),
          const SizedBox(height: 18),
          PrimaryButton(
            label: 'Fazer encomenda',
            icon: Icons.arrow_forward_rounded,
            onPressed: () => Navigator.pushNamed(context, AppRoutes.orderTracking),
          ),
          const SizedBox(height: 12),
          const Center(
            child: Text.rich(
              TextSpan(
                text: 'Ao clicar, você concorda com os nossos ',
                children: [TextSpan(text: 'Termos de Serviço', style: TextStyle(decoration: TextDecoration.underline))],
              ),
              style: TextStyle(color: AppColors.mutedText, fontSize: 12),
            ),
          ),
        ],
      ),
    );
  }
}

class _CheckoutLine extends StatelessWidget {
  const _CheckoutLine({required this.emoji, required this.title, required this.subtitle, required this.value});

  final String emoji;
  final String title;
  final String subtitle;
  final double value;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Container(
          width: 54,
          height: 54,
          decoration: BoxDecoration(color: AppColors.greenSoft, borderRadius: BorderRadius.circular(14)),
          child: Center(child: Text(emoji, style: const TextStyle(fontSize: 24))),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title, style: const TextStyle(fontWeight: FontWeight.w900)),
            Text(subtitle, style: const TextStyle(color: AppColors.mutedText, fontSize: 12)),
          ]),
        ),
        MoneyText(value, color: AppColors.text),
      ],
    );
  }
}

class _AmountRow extends StatelessWidget {
  const _AmountRow({required this.label, required this.value, this.strong = false});

  final String label;
  final double value;
  final bool strong;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 9),
      child: Row(children: [
        Expanded(child: Text(label, style: TextStyle(color: strong ? AppColors.text : AppColors.mutedText, fontWeight: strong ? FontWeight.w900 : FontWeight.w600))),
        MoneyText(value, color: strong ? AppColors.primary : AppColors.text),
      ]),
    );
  }
}

class _PaymentOption extends StatelessWidget {
  const _PaymentOption({required this.selected, required this.icon, required this.title, required this.subtitle});

  final bool selected;
  final IconData icon;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(AppRadii.lg),
        border: Border.all(color: selected ? AppColors.green : AppColors.border, width: selected ? 1.4 : 1),
      ),
      child: Row(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(color: selected ? AppColors.primary : AppColors.surfaceSoft, borderRadius: BorderRadius.circular(13)),
            child: Icon(icon, color: selected ? Colors.white : AppColors.mutedText),
          ),
          const SizedBox(width: 12),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title, style: const TextStyle(fontWeight: FontWeight.w900)),
            Text(subtitle, style: const TextStyle(color: AppColors.mutedText, fontSize: 12)),
          ])),
          Icon(selected ? Icons.check_circle_rounded : Icons.radio_button_unchecked_rounded, color: selected ? AppColors.green : AppColors.border),
        ],
      ),
    );
  }
}
