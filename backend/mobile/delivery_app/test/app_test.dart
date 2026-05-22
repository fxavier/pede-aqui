import 'package:flutter_test/flutter_test.dart';
import 'package:pede_aqui_delivery_app/main.dart';

void main() {
  testWidgets('renderiza app cliente', (tester) async {
    await tester.pumpWidget(const DeliveryCustomerApp());
    expect(find.text('Entrar - Pede Aqui'), findsOneWidget);
    expect(find.text('Entrar com Keycloak'), findsOneWidget);
  });
}
