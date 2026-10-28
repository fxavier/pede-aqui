import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../app.dart';
import '../../core/constants/app_colors.dart';
import '../cubits/delivery/delivery_cubit.dart';
import '../cubits/delivery/delivery_state.dart';
import '../widgets/address_card.dart';
import '../widgets/app_top_bar.dart';
import '../widgets/map_preview.dart';
import '../widgets/status_stepper.dart';

class DeliveryDetailScreen extends StatelessWidget {
  const DeliveryDetailScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<DeliveryCubit, DeliveryState>(
      builder: (context, state) {
        final delivery = state.delivery;
        return Scaffold(
          appBar: AppTopBar(
            title: 'Detalhes da Entrega',
            subtitle: delivery == null ? null : 'REF: ${delivery.reference}',
            showBack: true,
            actions: [IconButton(onPressed: () {}, icon: const Icon(Icons.more_vert, color: AppColors.onSurfaceVariant))],
          ),
          body: delivery == null
              ? const Center(child: CircularProgressIndicator())
              : ListView(
                  padding: const EdgeInsets.fromLTRB(18, 18, 18, 118),
                  children: [
                    DeliveryStatusStepper(currentStatus: delivery.status),
                    const SizedBox(height: 24),
                    MapPreview(estimatedMinutes: delivery.estimatedMinutes),
                    const SizedBox(height: 24),
                    AddressCard(
                      location: delivery.vendor,
                      icon: Icons.local_pizza_outlined,
                      statusLabel: delivery.pickupCompleted ? 'RECOLHA CONCLUÍDA' : 'RECOLHA PENDENTE',
                      color: AppColors.secondary,
                    ),
                    const SizedBox(height: 18),
                    AddressCard(
                      location: delivery.destination,
                      icon: Icons.person_pin_circle_outlined,
                      statusLabel: 'PRÓXIMO DESTINO',
                      color: AppColors.primary,
                      isNextDestination: true,
                    ),
                    const SizedBox(height: 22),
                    Wrap(
                      spacing: 10,
                      runSpacing: 10,
                      children: [
                        ...delivery.items.map((item) => _Chip(label: item.label)),
                        _Chip(label: delivery.paymentMethod),
                        _Chip(label: '${delivery.distanceKm.toStringAsFixed(1)} km'),
                      ],
                    ),
                  ],
                ),
          bottomNavigationBar: SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(18, 10, 18, 18),
              child: FilledButton.icon(
                onPressed: delivery == null ? null : () => Navigator.of(context).pushNamed(AppRoutes.confirmDelivery),
                icon: const Icon(Icons.how_to_reg_rounded),
                label: const Text('Cheguei ao cliente'),
              ),
            ),
          ),
        );
      },
    );
  }
}

class _Chip extends StatelessWidget {
  const _Chip({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(color: AppColors.surfaceContainerHighest, borderRadius: BorderRadius.circular(12)),
      child: Text(label, style: const TextStyle(color: AppColors.onSurfaceVariant, fontWeight: FontWeight.w700)),
    );
  }
}
