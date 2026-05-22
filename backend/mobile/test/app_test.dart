import 'package:delivery_marketplace_mobile/main.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('renders MVP app shell', (tester) async {
    await tester.pumpWidget(const DeliveryMarketplaceApp());
    expect(find.text('Pede Aqui - Mozambique'), findsOneWidget);
    expect(find.text('Inicio'), findsOneWidget);
  });
}
