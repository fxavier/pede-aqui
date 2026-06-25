import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../core/constants/app_colors.dart';
import '../../../core/constants/app_shadows.dart';
import '../../../core/constants/app_spacing.dart';
import '../../../shared/widgets/app_bottom_nav.dart';
import '../../../shared/widgets/money_text.dart';
import '../../../shared/widgets/screen_shell.dart';
import '../data/order_models.dart';
import 'order_tracking_cubit.dart';

class OrderTrackingScreen extends StatefulWidget {
  const OrderTrackingScreen({super.key});

  @override
  State<OrderTrackingScreen> createState() => _OrderTrackingScreenState();
}

class _OrderTrackingScreenState extends State<OrderTrackingScreen> {
  @override
  void initState() {
    super.initState();
    context.read<OrderTrackingCubit>().loadActiveOrder();
  }

  @override
  Widget build(BuildContext context) {
    return ScreenShell(
      bottomNavigationBar: const AppBottomNav(currentIndex: 3),
      padding: const EdgeInsets.fromLTRB(20, 0, 20, 116),
      appBar: AppBar(
        title: const Text('Encomenda #PA-2026-00891', style: TextStyle(fontFamily: 'Fraunces', fontWeight: FontWeight.w800, fontSize: 16)),
        leading: IconButton(onPressed: () => Navigator.pop(context), icon: const Icon(Icons.arrow_back_rounded)),
        actions: [IconButton(onPressed: () {}, icon: const Icon(Icons.notifications_none_rounded))],
      ),
      child: BlocBuilder<OrderTrackingCubit, OrderTrackingState>(
        builder: (context, state) {
          final order = state.order;
          if (state.loading && order == null) {
            return const Center(child: CircularProgressIndicator());
          }
          if (order == null) {
            return const Text('Nenhuma encomenda ativa.');
          }

          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                height: 235,
                width: double.infinity,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(28),
                  gradient: const LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [AppColors.forestLight, AppColors.forest, AppColors.forestDark],
                  ),
                  boxShadow: AppShadows.warmMd,
                ),
                child: Stack(
                  children: [
                    Positioned(
                      top: 24,
                      left: 0,
                      right: 0,
                      child: Center(
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                          decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(999)),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(Icons.delivery_dining_rounded, color: AppColors.primary),
                              const SizedBox(width: 8),
                              Text('Chegada em aprox. ${order.estimatedMinutes} min', style: const TextStyle(fontWeight: FontWeight.w900)),
                            ],
                          ),
                        ),
                      ),
                    ),
                    const Positioned(left: 34, bottom: 40, child: _MapDot(color: AppColors.green)),
                    const Positioned(right: 48, bottom: 66, child: _MapDot(color: AppColors.primary)),
                    Positioned(left: 56, bottom: 52, right: 70, child: Container(height: 4, color: Colors.white70)),
                  ],
                ),
              ),
              const SizedBox(height: 18),
              _Timeline(steps: order.steps),
              const SizedBox(height: 20),
              const Text('Código de Entrega', style: TextStyle(fontFamily: 'Fraunces', fontSize: 18, fontWeight: FontWeight.w800)),
              const SizedBox(height: 6),
              const Text('Forneça este código ao estafeta no momento da entrega.', style: TextStyle(color: AppColors.mutedText)),
              const SizedBox(height: 14),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: const ['4', '7', '2', '9', '8', '1'].map((digit) => _CodeBox(digit: digit)).toList(),
              ),
              const SizedBox(height: 24),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(AppRadii.card), border: Border.all(color: AppColors.border, width: 0.5), boxShadow: AppShadows.warm),
                child: Row(
                  children: [
                    const CircleAvatar(radius: 26, backgroundColor: AppColors.greenSoft, child: Text('KM', style: TextStyle(color: AppColors.green, fontWeight: FontWeight.w900))),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(order.courierName, style: const TextStyle(fontWeight: FontWeight.w900)),
                          const Text('4.9 • 214 entregas', style: TextStyle(color: AppColors.mutedText, fontSize: 12)),
                        ],
                      ),
                    ),
                    IconButton.filled(
                      onPressed: () {},
                      style: IconButton.styleFrom(backgroundColor: AppColors.green),
                      icon: const Icon(Icons.call_rounded),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 18),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(AppRadii.card), border: Border.all(color: AppColors.border, width: 0.5), boxShadow: AppShadows.warm),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Detalhes do Pedido', style: TextStyle(fontFamily: 'Fraunces', fontSize: 18, fontWeight: FontWeight.w800)),
                    const SizedBox(height: 12),
                    ...order.lines.map((line) => Padding(
                          padding: const EdgeInsets.only(bottom: 12),
                          child: _OrderLineTile(line: line),
                        )),
                    const Divider(height: 24),
                    _AmountRow(label: 'Subtotal', value: order.subtotal),
                    _AmountRow(label: 'Taxa de Entrega', value: order.deliveryFee),
                    _AmountRow(label: 'Total', value: order.total, strong: true),
                  ],
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _MapDot extends StatelessWidget {
  const _MapDot({required this.color});

  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(width: 34, height: 34, decoration: BoxDecoration(color: color, shape: BoxShape.circle, border: Border.all(color: Colors.white, width: 4)));
  }
}

class _Timeline extends StatelessWidget {
  const _Timeline({required this.steps});

  final List<OrderStatusStep> steps;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 16),
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(22), border: Border.all(color: AppColors.border)),
      child: Row(
        children: steps.map((step) {
          return Expanded(
            child: Column(
              children: [
                Container(
                  width: 30,
                  height: 30,
                  decoration: BoxDecoration(color: step.completed ? AppColors.green : AppColors.primarySoft, shape: BoxShape.circle),
                  child: Icon(step.completed ? Icons.check_rounded : Icons.more_horiz_rounded, color: step.completed ? Colors.white : AppColors.primary, size: 16),
                ),
                const SizedBox(height: 8),
                Text(step.title, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 10, fontWeight: FontWeight.w900, color: step.completed ? AppColors.text : AppColors.mutedText)),
              ],
            ),
          );
        }).toList(),
      ),
    );
  }
}

class _CodeBox extends StatelessWidget {
  const _CodeBox({required this.digit});

  final String digit;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 48,
      height: 58,
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(AppRadii.md), border: Border.all(color: AppColors.border)),
      child: Center(child: Text(digit, style: const TextStyle(fontFamily: 'Fraunces', fontSize: 22, fontWeight: FontWeight.w800))),
    );
  }
}

class _OrderLineTile extends StatelessWidget {
  const _OrderLineTile({required this.line});

  final OrderLine line;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Text(line.emoji, style: const TextStyle(fontSize: 24)),
        const SizedBox(width: 10),
        Expanded(child: Text('${line.quantity}x ${line.name}', style: const TextStyle(fontWeight: FontWeight.w800))),
        MoneyText(line.price, color: AppColors.text),
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
        MoneyText(value, large: strong, color: strong ? AppColors.primary : AppColors.text),
      ]),
    );
  }
}
