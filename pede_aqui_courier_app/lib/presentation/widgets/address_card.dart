import 'package:flutter/material.dart';

import '../../core/constants/app_colors.dart';
import '../../data/models/delivery_models.dart';
import 'app_card.dart';

class AddressCard extends StatelessWidget {
  const AddressCard({
    super.key,
    required this.location,
    required this.icon,
    required this.statusLabel,
    required this.color,
    this.isNextDestination = false,
  });

  final DeliveryLocation location;
  final IconData icon;
  final String statusLabel;
  final Color color;
  final bool isNextDestination;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(24),
        border: Border(left: BorderSide(color: color, width: 4)),
      ),
      child: AppCard(
        padding: EdgeInsets.zero,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 56,
                    height: 56,
                    decoration: BoxDecoration(color: color.withOpacity(.12), borderRadius: BorderRadius.circular(14)),
                    child: Icon(icon, color: color, size: 28),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(location.name, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: AppColors.onSurface)),
                        const SizedBox(height: 4),
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Icon(location.note == null ? Icons.location_on_outlined : Icons.notes_rounded, size: 19, color: AppColors.onSurfaceVariant),
                            const SizedBox(width: 4),
                            Expanded(
                              child: Text(
                                location.note ?? '${location.address}, ${location.district}',
                                style: TextStyle(
                                  color: AppColors.onSurfaceVariant,
                                  height: 1.35,
                                  fontSize: 15,
                                  fontStyle: location.note == null ? FontStyle.normal : FontStyle.italic,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  OutlinedButton.icon(
                    onPressed: () {},
                    icon: const Icon(Icons.call_outlined, size: 18),
                    label: const Text('Ligar'),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: color,
                      side: BorderSide(color: color.withOpacity(.25)),
                      backgroundColor: color.withOpacity(.08),
                      minimumSize: const Size(0, 44),
                      padding: const EdgeInsets.symmetric(horizontal: 14),
                    ),
                  ),
                ],
              ),
            ),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(
                color: color.withOpacity(.07),
                border: Border(top: BorderSide(color: color.withOpacity(.12))),
                borderRadius: const BorderRadius.vertical(bottom: Radius.circular(24)),
              ),
              child: Row(
                children: [
                  Icon(isNextDestination ? Icons.info_outline : Icons.check_circle_outline, size: 20, color: color),
                  const SizedBox(width: 8),
                  Text(statusLabel, style: TextStyle(color: color, fontWeight: FontWeight.w900, letterSpacing: .4)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
