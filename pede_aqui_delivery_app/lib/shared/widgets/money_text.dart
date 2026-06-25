import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../core/constants/app_colors.dart';

/// Mirrors the web `formatMZN`: grouped thousands, no forced decimals, "MT" suffix.
final _moneyFormatter = NumberFormat.decimalPattern('pt_MZ')..maximumFractionDigits = 0;

class MoneyText extends StatelessWidget {
  const MoneyText(this.value, {super.key, this.large = false, this.color = AppColors.primary});

  final double value;
  final bool large;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Text(
      '${_moneyFormatter.format(value)} MT',
      style: TextStyle(
        color: color,
        fontWeight: FontWeight.w700,
        fontSize: large ? 22 : 14,
      ),
    );
  }
}
