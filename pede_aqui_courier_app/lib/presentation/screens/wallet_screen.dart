import 'package:flutter/material.dart';

import '../../core/constants/app_colors.dart';
import '../../core/utils/mzn_formatter.dart';
import '../widgets/app_card.dart';
import '../widgets/app_top_bar.dart';

class WalletScreen extends StatelessWidget {
  const WalletScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: const AppTopBar(title: 'Carteira', showBack: true),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(18, 18, 18, 32),
        children: [
          Container(
            padding: const EdgeInsets.all(22),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(26),
              gradient: const LinearGradient(colors: [AppColors.primary, Color(0xFF6E1600)]),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Saldo disponível', style: TextStyle(color: Colors.white70, fontWeight: FontWeight.w700)),
                const SizedBox(height: 8),
                Text(MznFormatter.amount(9850), style: const TextStyle(color: Colors.white, fontSize: 36, fontWeight: FontWeight.w900)),
                const SizedBox(height: 18),
                FilledButton(
                  onPressed: () {},
                  style: FilledButton.styleFrom(backgroundColor: Colors.white, foregroundColor: AppColors.primary),
                  child: const Text('Solicitar levantamento'),
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          const AppCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Método de pagamento', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
                SizedBox(height: 16),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: CircleAvatar(backgroundColor: AppColors.secondaryContainer, child: Icon(Icons.phone_iphone, color: AppColors.secondary)),
                  title: Text('M-Pesa', style: TextStyle(fontWeight: FontWeight.w900)),
                  subtitle: Text('+258 84 123 4567'),
                  trailing: Icon(Icons.check_circle, color: AppColors.secondary),
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          const AppCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Últimos movimentos', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
                SizedBox(height: 12),
                _WalletRow(title: 'Entrega #PA-00891', amount: '+320 MT', date: 'Hoje, 13:45'),
                _WalletRow(title: 'Levantamento M-Pesa', amount: '-3.000 MT', date: 'Ontem, 18:30'),
                _WalletRow(title: 'Bónus Turbo', amount: '+180 MT', date: 'Ontem, 21:00'),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _WalletRow extends StatelessWidget {
  const _WalletRow({required this.title, required this.amount, required this.date});

  final String title;
  final String amount;
  final String date;

  @override
  Widget build(BuildContext context) {
    final isPositive = amount.startsWith('+');
    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
      subtitle: Text(date),
      trailing: Text(amount, style: TextStyle(color: isPositive ? AppColors.secondary : AppColors.primary, fontWeight: FontWeight.w900)),
    );
  }
}
