import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../app/pede_aqui_app.dart';
import '../../../core/constants/app_colors.dart';
import '../../../shared/widgets/app_bottom_nav.dart';
import '../../../shared/widgets/money_text.dart';
import '../../../shared/widgets/order_summary_card.dart';
import '../../../shared/widgets/primary_button.dart';
import '../../../shared/widgets/screen_shell.dart';
import '../data/cart_models.dart';
import 'cart_cubit.dart';

class CartScreen extends StatelessWidget {
  const CartScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ScreenShell(
      bottomNavigationBar: const AppBottomNav(currentIndex: 2),
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 116),
      child: BlocBuilder<CartCubit, CartState>(
        builder: (context, state) {
          final summary = state.summary;
          if (state.loading && summary == null) {
            return const Center(child: CircularProgressIndicator());
          }
          if (summary == null) {
            return const Text('Carrinho vazio');
          }

          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 42,
                    height: 42,
                    decoration: BoxDecoration(color: AppColors.greenSoft, borderRadius: BorderRadius.circular(14)),
                    child: const Icon(Icons.location_on_rounded, color: AppColors.green),
                  ),
                  const SizedBox(width: 12),
                  const Expanded(
                    child: Text('Carrinho — Avenida Gourmet', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
                  ),
                  IconButton(onPressed: () {}, icon: const Icon(Icons.notifications_none_rounded)),
                ],
              ),
              const SizedBox(height: 20),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(22), border: Border.all(color: AppColors.border)),
                child: Row(
                  children: [
                    const Icon(Icons.location_on_outlined, color: AppColors.green),
                    const SizedBox(width: 10),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Entregar em', style: TextStyle(color: AppColors.mutedText, fontSize: 12)),
                          Text('Av. Julius Nyerere, 123, Polana', style: TextStyle(fontWeight: FontWeight.w900)),
                        ],
                      ),
                    ),
                    IconButton(onPressed: () {}, icon: const Icon(Icons.edit_rounded, color: AppColors.primary)),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              const Text('Os teus itens', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
              const SizedBox(height: 14),
              ...summary.items.map((item) => Padding(
                    padding: const EdgeInsets.only(bottom: 14),
                    child: _CartItemTile(item: item),
                  )),
              const SizedBox(height: 10),
              OrderSummaryCard(summary: summary),
              const SizedBox(height: 18),
              PrimaryButton(
                label: 'Confirmar encomenda',
                icon: Icons.arrow_forward_rounded,
                onPressed: () => Navigator.pushNamed(context, AppRoutes.checkout),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _CartItemTile extends StatelessWidget {
  const _CartItemTile({required this.item});

  final CartItem item;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(22), border: Border.all(color: AppColors.border)),
      child: Row(
        children: [
          Container(
            width: 82,
            height: 82,
            decoration: BoxDecoration(color: AppColors.primarySoft, borderRadius: BorderRadius.circular(18)),
            child: Center(child: Text(item.emoji, style: const TextStyle(fontSize: 38))),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(item.name, style: const TextStyle(fontWeight: FontWeight.w900)),
                const SizedBox(height: 7),
                Text('${item.quantity} unidades', style: const TextStyle(color: AppColors.mutedText, fontSize: 12)),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              MoneyText(item.total),
              const SizedBox(height: 10),
              Container(
                decoration: BoxDecoration(color: AppColors.primarySoft, borderRadius: BorderRadius.circular(999)),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    _QtyButton(icon: Icons.remove_rounded, onTap: () => context.read<CartCubit>().updateQuantity(item.productId, item.quantity - 1)),
                    Text(item.quantity.toString(), style: const TextStyle(fontWeight: FontWeight.w900)),
                    _QtyButton(icon: Icons.add_rounded, onTap: () => context.read<CartCubit>().updateQuantity(item.productId, item.quantity + 1)),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _QtyButton extends StatelessWidget {
  const _QtyButton({required this.icon, required this.onTap});

  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 34,
      height: 32,
      child: IconButton(onPressed: onTap, icon: Icon(icon, size: 16), color: AppColors.primary),
    );
  }
}
