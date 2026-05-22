abstract final class MznFormatter {
  static String amount(num value) => '${_withThousands(value)} MT';

  static String amountWithIso(num value) => '${_withThousands(value)} MZN';

  static String _withThousands(num value) {
    final whole = value.round().toString();
    final buffer = StringBuffer();
    for (var i = 0; i < whole.length; i++) {
      final remaining = whole.length - i;
      buffer.write(whole[i]);
      if (remaining > 1 && remaining % 3 == 1) {
        buffer.write('.');
      }
    }
    return buffer.toString();
  }
}
