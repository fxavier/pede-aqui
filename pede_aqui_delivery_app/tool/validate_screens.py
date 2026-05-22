#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
expected = {
    'pede_aqui_landing_page': ('/landing', 'lib/features/catalog/presentation/landing_screen.dart', 'assets/reference_screens/pede_aqui_landing_page.png'),
    'onboarding_pede_aqui': ('/onboarding', 'lib/features/catalog/presentation/onboarding_screen.dart', 'assets/reference_screens/onboarding_pede_aqui.png'),
    'login_register_pede_aqui': ('/auth', 'lib/features/auth/presentation/login_register_screen.dart', 'assets/reference_screens/login_register_pede_aqui.png'),
    'home_pede_aqui': ('/home', 'lib/features/catalog/presentation/home_screen.dart', 'assets/reference_screens/home_pede_aqui.png'),
    'store_pede_aqui': ('/store', 'lib/features/catalog/presentation/store_screen.dart', 'assets/reference_screens/store_pede_aqui.png'),
    'cart_pede_aqui': ('/cart', 'lib/features/cart/presentation/cart_screen.dart', 'assets/reference_screens/cart_pede_aqui.png'),
    'checkout_pede_aqui': ('/checkout', 'lib/features/checkout/presentation/checkout_screen.dart', 'assets/reference_screens/checkout_pede_aqui.png'),
    'checkout_with_promotion_pede_aqui': ('/checkout-promo', 'lib/features/checkout/presentation/checkout_promotion_screen.dart', 'assets/reference_screens/checkout_with_promotion_pede_aqui.png'),
    'order_tracking_pede_aqui': ('/order-tracking', 'lib/features/orders/presentation/order_tracking_screen.dart', 'assets/reference_screens/order_tracking_pede_aqui.png'),
}

app_file = root / 'lib/app/pede_aqui_app.dart'
app_text = app_file.read_text()
errors = []
for screen, (route, dart_file, image_file) in expected.items():
    if route not in app_text:
        errors.append(f'Missing route {route} for {screen}')
    if not (root / dart_file).exists():
        errors.append(f'Missing Dart screen file {dart_file}')
    if not (root / image_file).exists():
        errors.append(f'Missing reference image {image_file}')

if errors:
    print('\n'.join(errors))
    sys.exit(1)

print(f'Screen coverage OK: {len(expected)} ZIP screens have routes, Dart screens and reference images.')
