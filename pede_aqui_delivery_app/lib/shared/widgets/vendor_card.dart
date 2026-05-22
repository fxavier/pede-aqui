import 'package:flutter/material.dart';

import '../../app/pede_aqui_app.dart';
import '../../core/constants/app_colors.dart';
import '../../features/catalog/data/catalog_models.dart';
import 'status_chip.dart';

class VendorCard extends StatelessWidget {
  const VendorCard({required this.vendor, super.key, this.horizontal = false});

  final Vendor vendor;
  final bool horizontal;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(24),
      onTap: () => Navigator.pushNamed(context, AppRoutes.store),
      child: Container(
        width: horizontal ? 190 : double.infinity,
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(24),
          border: Border.all(color: AppColors.border),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(.04),
              blurRadius: 22,
              offset: const Offset(0, 10),
            ),
          ],
        ),
        child: horizontal ? _Horizontal(vendor: vendor) : _Vertical(vendor: vendor),
      ),
    );
  }
}

class _Horizontal extends StatelessWidget {
  const _Horizontal({required this.vendor});

  final Vendor vendor;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _Cover(vendor: vendor, height: 86),
        const SizedBox(height: 10),
        Text(vendor.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w900)),
        const SizedBox(height: 4),
        Text('${vendor.deliveryMinutes} min • ${vendor.distanceKm.toStringAsFixed(1)} km', style: const TextStyle(fontSize: 12, color: AppColors.mutedText)),
      ],
    );
  }
}

class _Vertical extends StatelessWidget {
  const _Vertical({required this.vendor});

  final Vendor vendor;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        _Cover(vendor: vendor, height: 78, width: 78),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(vendor.name, style: const TextStyle(fontWeight: FontWeight.w900)),
              const SizedBox(height: 4),
              Text(vendor.category, style: const TextStyle(fontSize: 12, color: AppColors.mutedText)),
              const SizedBox(height: 8),
              Row(
                children: [
                  StatusChip(label: vendor.rating.toStringAsFixed(1), icon: Icons.star_rounded, green: true),
                  const SizedBox(width: 8),
                  Text('${vendor.deliveryMinutes}-${vendor.deliveryMinutes + 10} min', style: const TextStyle(fontSize: 12, color: AppColors.mutedText)),
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _Cover extends StatelessWidget {
  const _Cover({required this.vendor, required this.height, this.width});

  final Vendor vendor;
  final double height;
  final double? width;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        gradient: const LinearGradient(colors: [Color(0xFF321711), Color(0xFFC9280A)]),
        borderRadius: BorderRadius.circular(18),
      ),
      child: Center(child: Text(vendor.coverEmoji, style: const TextStyle(fontSize: 36))),
    );
  }
}
