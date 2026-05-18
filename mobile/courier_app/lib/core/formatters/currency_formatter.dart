import 'package:intl/intl.dart';

final NumberFormat _mznFormatter = NumberFormat.currency(
  locale: 'pt_MZ',
  symbol: 'MZN',
  decimalDigits: 2,
);

String formatMzn(num value) => _mznFormatter.format(value);
