import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../core/constants/app_colors.dart';
import '../cubits/earnings/earnings_cubit.dart';
import '../cubits/earnings/earnings_state.dart';
import '../widgets/app_top_bar.dart';
import '../widgets/earnings_widgets.dart';
import '../widgets/section_header.dart';

class EarningsScreen extends StatelessWidget {
  const EarningsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: const AppTopBar(title: 'Os meus ganhos', showAvatar: true),
      body: BlocBuilder<EarningsCubit, EarningsState>(
        builder: (context, state) {
          if (state.isLoading && state.summary == null) {
            return const Center(child: CircularProgressIndicator());
          }

          return ListView(
            padding: const EdgeInsets.fromLTRB(0, 18, 0, 112),
            children: [
              if (state.summary != null) EarningsSummaryCards(summary: state.summary!),
              const SizedBox(height: 22),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 18),
                child: WeeklyChartCard(weekly: state.weekly),
              ),
              const SizedBox(height: 22),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 18),
                child: SectionHeader(title: 'Histórico recente', actionLabel: 'Ver todos', onAction: () {}),
              ),
              const SizedBox(height: 10),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 18),
                child: Column(
                  children: state.history
                      .map((record) => Padding(
                            padding: const EdgeInsets.only(bottom: 14),
                            child: EarningHistoryItem(record: record),
                          ))
                      .toList(),
                ),
              ),
              const SizedBox(height: 12),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 18),
                child: Container(
                  padding: const EdgeInsets.all(22),
                  decoration: BoxDecoration(color: AppColors.primary, borderRadius: BorderRadius.circular(24)),
                  child: const Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Bónus Turbo', style: TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w900)),
                            SizedBox(height: 8),
                            Text('Ganhe 1.5x em todas as entregas na Baixa entre 18:00 e 21:00.', style: TextStyle(color: Colors.white, fontSize: 16, height: 1.35)),
                          ],
                        ),
                      ),
                      CircleAvatar(radius: 34, backgroundColor: Colors.white24, child: Icon(Icons.bolt, color: Colors.white, size: 38)),
                    ],
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
