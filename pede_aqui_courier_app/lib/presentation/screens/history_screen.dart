import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../core/constants/app_colors.dart';
import '../cubits/earnings/earnings_cubit.dart';
import '../cubits/earnings/earnings_state.dart';
import '../widgets/app_top_bar.dart';
import '../widgets/earnings_widgets.dart';

class HistoryScreen extends StatelessWidget {
  const HistoryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: const AppTopBar(title: 'Histórico'),
      body: BlocBuilder<EarningsCubit, EarningsState>(
        builder: (context, state) {
          return ListView(
            padding: const EdgeInsets.fromLTRB(18, 18, 18, 112),
            children: [
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(color: AppColors.surfaceContainer, borderRadius: BorderRadius.circular(22)),
                child: const Row(
                  children: [
                    Icon(Icons.filter_list_rounded, color: AppColors.primary),
                    SizedBox(width: 10),
                    Expanded(child: Text('Últimos 30 dias • Maputo', style: TextStyle(fontWeight: FontWeight.w800))),
                    Icon(Icons.keyboard_arrow_down_rounded, color: AppColors.primary),
                  ],
                ),
              ),
              const SizedBox(height: 18),
              ...state.history.map((record) => Padding(
                    padding: const EdgeInsets.only(bottom: 14),
                    child: EarningHistoryItem(record: record),
                  )),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: () {},
                icon: const Icon(Icons.receipt_long_outlined),
                label: const Text('Exportar comprovativos'),
              ),
            ],
          );
        },
      ),
    );
  }
}
