import 'package:flutter/material.dart';

import '../../core/constants/app_colors.dart';
import '../../core/utils/mzn_formatter.dart';
import '../../data/models/earnings_models.dart';
import 'app_card.dart';

class EarningsSummaryCards extends StatelessWidget {
  const EarningsSummaryCards({super.key, required this.summary});

  final EarningSummary summary;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 136,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        children: [
          _SummaryCard(title: 'Hoje', amount: summary.today, color: AppColors.primary),
          _SummaryCard(title: 'Esta semana', amount: summary.week, color: AppColors.secondary),
          _SummaryCard(title: 'Este mês', amount: summary.month, color: AppColors.tertiary),
        ],
      ),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({required this.title, required this.amount, required this.color});

  final String title;
  final int amount;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 164,
      margin: const EdgeInsets.only(right: 16),
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border(bottom: BorderSide(color: color, width: 4)),
        boxShadow: const [BoxShadow(color: Color(0x142C2C3A), blurRadius: 16, offset: Offset(0, 8))],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(title, style: const TextStyle(color: AppColors.onSurfaceVariant, fontWeight: FontWeight.w800)),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(MznFormatter.amount(amount).replaceAll(' MT', ''), style: const TextStyle(fontSize: 25, fontWeight: FontWeight.w700)),
              Text('MT', style: TextStyle(color: color, fontWeight: FontWeight.w900)),
            ],
          ),
        ],
      ),
    );
  }
}

class WeeklyChartCard extends StatelessWidget {
  const WeeklyChartCard({super.key, required this.weekly});

  final List<WeeklyEarning> weekly;

  @override
  Widget build(BuildContext context) {
    final maxAmount = weekly.map((e) => e.amount).fold<int>(0, (a, b) => a > b ? a : b);
    final total = weekly.fold<int>(0, (sum, item) => sum + item.amount);

    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Ganhos da semana', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
                    Text('Total: ${MznFormatter.amount(total)}', style: const TextStyle(color: AppColors.onSurfaceVariant)),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
                decoration: BoxDecoration(color: AppColors.surfaceContainerHighest, borderRadius: BorderRadius.circular(12)),
                child: const Text('+12% vs semana anterior', style: TextStyle(color: AppColors.primary, fontWeight: FontWeight.w900)),
              ),
            ],
          ),
          const SizedBox(height: 22),
          SizedBox(
            height: 190,
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: weekly.map((item) {
                final factor = maxAmount == 0 ? 0.0 : item.amount / maxAmount;
                return Expanded(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      AnimatedContainer(
                        duration: const Duration(milliseconds: 500),
                        width: 28,
                        height: 34 + (128 * factor),
                        decoration: BoxDecoration(
                          color: item.isToday ? AppColors.primary.withOpacity(.78) : AppColors.primary,
                          borderRadius: const BorderRadius.vertical(top: Radius.circular(18)),
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(item.day, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w800, color: item.isToday ? AppColors.primary : AppColors.onSurfaceVariant)),
                    ],
                  ),
                );
              }).toList(),
            ),
          ),
        ],
      ),
    );
  }
}

class EarningHistoryItem extends StatelessWidget {
  const EarningHistoryItem({super.key, required this.record});

  final EarningRecord record;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      padding: const EdgeInsets.all(14),
      child: Row(
        children: [
          Container(
            width: 54,
            height: 54,
            decoration: BoxDecoration(color: AppColors.surfaceContainer, borderRadius: BorderRadius.circular(14)),
            child: Icon(_iconFor(record.category), color: AppColors.primary),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(record.vendorName, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w900)),
                Text(record.timeLabel, style: const TextStyle(color: AppColors.onSurfaceVariant)),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(MznFormatter.amount(record.amount), style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(color: AppColors.secondaryContainer, borderRadius: BorderRadius.circular(999)),
                child: Text(record.status.toUpperCase(), style: const TextStyle(fontSize: 10, color: AppColors.secondary, fontWeight: FontWeight.w900)),
              ),
            ],
          ),
        ],
      ),
    );
  }

  IconData _iconFor(String category) => switch (category) {
        'shopping_bag' => Icons.shopping_bag_outlined,
        'local_pizza' => Icons.local_pizza_outlined,
        'local_pharmacy' => Icons.local_pharmacy_outlined,
        _ => Icons.restaurant_outlined,
      };
}
