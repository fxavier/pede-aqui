import 'package:flutter/material.dart';

import '../../app.dart';
import '../../core/constants/app_colors.dart';
import '../../core/utils/mzn_formatter.dart';
import '../../data/models/delivery_models.dart';
import 'app_card.dart';
import 'map_preview.dart';

class AvailabilityCard extends StatelessWidget {
  const AvailabilityCard({super.key, required this.isAvailable, required this.onChanged});

  final bool isAvailable;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('ESTADO', style: TextStyle(color: AppColors.onSurfaceVariant, fontWeight: FontWeight.w900, letterSpacing: .8)),
                Text(
                  isAvailable ? 'Disponível' : 'Indisponível',
                  style: TextStyle(
                    color: isAvailable ? AppColors.secondary : AppColors.error,
                    fontWeight: FontWeight.w900,
                    fontSize: 24,
                  ),
                ),
              ],
            ),
          ),
          Switch(value: isAvailable, activeColor: AppColors.secondary, onChanged: onChanged),
        ],
      ),
    );
  }
}

class EarningsPill extends StatelessWidget {
  const EarningsPill({super.key, required this.amount});

  final int amount;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.centerLeft,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
        decoration: BoxDecoration(color: AppColors.secondaryContainer, borderRadius: BorderRadius.circular(999)),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.payments_outlined, color: AppColors.secondary),
            const SizedBox(width: 8),
            Text('Ganhos de hoje: ${MznFormatter.amount(amount)}', style: const TextStyle(color: AppColors.secondary, fontWeight: FontWeight.w900)),
          ],
        ),
      ),
    );
  }
}

class ActiveDeliveryCard extends StatelessWidget {
  const ActiveDeliveryCard({super.key, required this.delivery});

  final Delivery delivery;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      padding: EdgeInsets.zero,
      child: Column(
        children: [
          MapPreview(height: 132, statusLabel: delivery.status.label, showEta: false),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Column(
                      children: [
                        const Icon(Icons.storefront_rounded, color: AppColors.secondary),
                        Container(width: 2, height: 28, margin: const EdgeInsets.symmetric(vertical: 4), color: AppColors.outlineVariant),
                        const Icon(Icons.location_on_rounded, color: AppColors.primary),
                      ],
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _RoutePoint(label: 'Recolha', value: delivery.vendor.name),
                          const SizedBox(height: 14),
                          _RoutePoint(label: 'Entrega', value: '${delivery.destination.district}, ${delivery.destination.address}'),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 18),
                FilledButton.icon(
                  onPressed: () => Navigator.of(context).pushNamed(AppRoutes.deliveryDetail),
                  icon: const Text('Ver Detalhes'),
                  label: const Icon(Icons.chevron_right_rounded),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _RoutePoint extends StatelessWidget {
  const _RoutePoint({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: const TextStyle(color: AppColors.onSurfaceVariant, fontWeight: FontWeight.w800)),
        Text(value, style: const TextStyle(color: AppColors.onSurface, fontSize: 16, fontWeight: FontWeight.w900)),
      ],
    );
  }
}

class AvailableJobCard extends StatelessWidget {
  const AvailableJobCard({super.key, required this.job, required this.onAccept, required this.onReject});

  final AvailableJob job;
  final VoidCallback onAccept;
  final VoidCallback onReject;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      child: Column(
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(job.vendorName, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
                    const SizedBox(height: 2),
                    Text('${job.distanceKm.toStringAsFixed(1)} km • ${job.pickupDistrict}', style: const TextStyle(color: AppColors.onSurfaceVariant)),
                  ],
                ),
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(MznFormatter.amount(job.estimatedEarning), style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w900, color: AppColors.secondary)),
                  const Text('Est. ganhos', style: TextStyle(color: AppColors.onSurfaceVariant, fontWeight: FontWeight.w800)),
                ],
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              const Icon(Icons.near_me_outlined, size: 18, color: AppColors.onSurfaceVariant),
              const SizedBox(width: 8),
              Expanded(child: Text('${job.pickupDistrict} → ${job.destinationDistrict}', style: const TextStyle(color: AppColors.onSurfaceVariant))),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(child: OutlinedButton(onPressed: onReject, child: const Text('Recusar'))),
              const SizedBox(width: 12),
              Expanded(flex: 2, child: FilledButton(onPressed: onAccept, style: FilledButton.styleFrom(backgroundColor: AppColors.secondary), child: const Text('Aceitar'))),
            ],
          ),
        ],
      ),
    );
  }
}
