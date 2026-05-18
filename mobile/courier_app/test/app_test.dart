import 'package:flutter_test/flutter_test.dart';
import 'package:pede_aqui_courier_app/main.dart';

void main() {
  testWidgets('renderiza app estafeta', (tester) async {
    await tester.pumpWidget(const CourierApp());
    expect(find.text('Pede Aqui Estafeta - Mocambique'), findsOneWidget);
    expect(find.text('Atribuicoes'), findsOneWidget);
  });
}
