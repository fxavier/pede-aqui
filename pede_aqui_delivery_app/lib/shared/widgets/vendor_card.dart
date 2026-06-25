import 'package:flutter/material.dart';

import '../../app/pede_aqui_app.dart';
import '../../core/constants/app_colors.dart';
import '../../core/constants/app_shadows.dart';
import '../../core/constants/app_spacing.dart';
import '../../features/catalog/data/catalog_models.dart';

/// Vendor tile mirrored from the web `VendorCard`: warm white card, pastel
/// cover gradient keyed off the vendor name + emoji, Aberto/Fechado/Rápido
/// badges, amber rating, time and delivery-fee row.
class VendorCard extends StatelessWidget {
  const VendorCard({required this.vendor, super.key, this.horizontal = false});

  final Vendor vendor;
  final bool horizontal;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(AppRadii.card),
      onTap: () => Navigator.pushNamed(context, AppRoutes.store),
      child: Container(
        width: horizontal ? 200 : double.infinity,
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(AppRadii.card),
          border: Border.all(color: AppColors.border, width: 0.5),
          boxShadow: AppShadows.warm,
        ),
        clipBehavior: Clip.antiAlias,
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
        _Cover(vendor: vendor, height: 112),
        Padding(
          padding: const EdgeInsets.all(AppSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(vendor.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14)),
              const SizedBox(height: 6),
              _MetaRow(vendor: vendor),
            ],
          ),
        ),
      ],
    );
  }
}

class _Vertical extends StatelessWidget {
  const _Vertical({required this.vendor});

  final Vendor vendor;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(AppSpacing.md),
      child: Row(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(AppRadii.md),
            child: _Cover(vendor: vendor, height: 78, width: 78),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(vendor.name, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14)),
                const SizedBox(height: 2),
                Text(vendor.category, style: const TextStyle(fontSize: 12, color: AppColors.mutedText)),
                const SizedBox(height: 8),
                _MetaRow(vendor: vendor),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Rating (amber) • time • delivery fee — mirrors the web meta row.
class _MetaRow extends StatelessWidget {
  const _MetaRow({required this.vendor});

  final Vendor vendor;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 10,
      runSpacing: 4,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.star_rounded, size: 14, color: AppColors.amber600),
            const SizedBox(width: 2),
            Text(vendor.rating.toStringAsFixed(1), style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: AppColors.amber600)),
          ],
        ),
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.schedule_rounded, size: 13, color: AppColors.mutedText),
            const SizedBox(width: 3),
            Text('${vendor.deliveryMinutes} min', style: const TextStyle(fontSize: 12, color: AppColors.mutedText)),
          ],
        ),
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.pedal_bike_rounded, size: 13, color: AppColors.mutedText),
            const SizedBox(width: 3),
            vendor.deliveryFee <= 0
                ? const Text('Grátis', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: AppColors.green))
                : Text('${vendor.deliveryFee.toStringAsFixed(0)} MT', style: const TextStyle(fontSize: 12, color: AppColors.mutedText)),
          ],
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
    final g = _coverGradient(vendor.name);
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        gradient: LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight, colors: g),
      ),
      child: Stack(
        children: [
          Center(child: Text(vendor.coverEmoji, style: const TextStyle(fontSize: 40))),
          if (!vendor.isOpen)
            Positioned.fill(
              child: ColoredBox(
                color: Colors.white.withValues(alpha: 0.78),
                child: const Center(
                  child: _Pill(label: 'Fechado', bg: Colors.white, fg: AppColors.mutedText),
                ),
              ),
            )
          else ...[
            Positioned(
              left: 8,
              top: 8,
              child: _Pill(
                label: 'Aberto',
                bg: Colors.white.withValues(alpha: 0.9),
                fg: AppColors.green,
                leading: Container(
                  width: 6,
                  height: 6,
                  decoration: const BoxDecoration(color: AppColors.green, shape: BoxShape.circle),
                ),
              ),
            ),
            if (vendor.deliveryMinutes <= 20)
              const Positioned(
                right: 8,
                top: 8,
                child: _Pill(label: 'Rápido 🔥', bg: AppColors.forest, fg: Colors.white),
              ),
          ],
        ],
      ),
    );
  }
}

class _Pill extends StatelessWidget {
  const _Pill({required this.label, required this.bg, required this.fg, this.leading});

  final String label;
  final Color bg;
  final Color fg;
  final Widget? leading;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(AppRadii.pill)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (leading != null) ...[leading!, const SizedBox(width: 4)],
          Text(label, style: TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: fg)),
        ],
      ),
    );
  }
}

/// Pastel cover gradient keyed off the vendor name — mirrors web `vendorVisual`.
List<Color> _coverGradient(String name) {
  final n = name.toLowerCase();
  if (n.contains('burger') || n.contains('fast')) return const [Color(0xFFFFF7E6), Color(0xFFFDE68A)];
  if (n.contains('pizza')) return const [Color(0xFFFFF5F5), Color(0xFFFECACA)];
  if (n.contains('sushi') || n.contains('zen')) return const [Color(0xFFF0F9FF), Color(0xFFBAE6FD)];
  if (n.contains('green') || n.contains('garden') || n.contains('saudável') || n.contains('bowl')) {
    return const [Color(0xFFF0FDF4), Color(0xFFBBF7D0)];
  }
  if (n.contains('farmácia') || n.contains('saúde')) return const [Color(0xFFEFF6FF), Color(0xFFBFDBFE)];
  if (n.contains('café') || n.contains('continental')) return const [Color(0xFFFFFBEB), Color(0xFFFDE68A)];
  if (n.contains('pastelaria') || n.contains('doce')) return const [Color(0xFFFDF2F8), Color(0xFFF9A8D4)];
  if (n.contains('super') || n.contains('mercado')) return const [Color(0xFFF7FEE7), Color(0xFFD9F99D)];
  return const [Color(0xFFFFF7ED), Color(0xFFFED7AA)];
}
