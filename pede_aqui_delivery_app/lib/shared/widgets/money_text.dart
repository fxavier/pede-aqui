import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../core/constants/app_colors.dart';

final _moneyFormatter = NumberFormat.currency(locale: 'pt_MZ', symbol: 'MT', decimalDigits: 2);

class MoneyText extends StatelessWidget {
  const MoneyText(this.value, {super.key, this.large = false, this.color = AppColors.primary});

  final double value;
  final bool large;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final formatted = _moneyFormatter.format(value)
        .replaceAll('MT', '')
        .replaceAll('MZN', '')
        .trim();
    return Text(
      '$formatted MT',
      style: TextStyle(
        color: color,
        fontWeight: FontWeight.w900,
        fontSize: large ? 22 : 14,
      ),
    );
  }
}
