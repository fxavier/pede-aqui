import 'package:flutter_test/flutter_test.dart';
import 'package:pede_aqui_mobile/app/pede_aqui_app.dart';

void main() {
  test('all uploaded screens are mapped to routes', () {
    expect(AppRoutes.screenMap.length, 9);
    expect(AppRoutes.screenMap['home_pede_aqui'], AppRoutes.home);
    expect(AppRoutes.screenMap['checkout_with_promotion_pede_aqui'], AppRoutes.checkoutPromo);
  });
}
