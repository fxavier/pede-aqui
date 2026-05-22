from pathlib import Path

root = Path(__file__).resolve().parents[1]
required = [
    'lib/main.dart',
    'lib/app.dart',
    'lib/core/di/injection.dart',
    'lib/core/network/api_client.dart',
    'lib/data/datasources/mock_courier_data_source.dart',
    'lib/data/datasources/remote_courier_data_source.dart',
    'lib/presentation/screens/home_dashboard_screen.dart',
    'lib/presentation/screens/delivery_detail_screen.dart',
    'lib/presentation/screens/delivery_confirmation_screen.dart',
    'lib/presentation/screens/earnings_screen.dart',
    'lib/presentation/screens/history_screen.dart',
    'lib/presentation/screens/profile_screen.dart',
    'lib/presentation/screens/wallet_screen.dart',
    'lib/presentation/screens/notifications_screen.dart',
    'lib/presentation/screens/settings_screen.dart',
    'assets/reference_screens/job_dashboard_courier_app.png',
    'assets/reference_screens/active_delivery_detail_courier_app.png',
    'assets/reference_screens/delivery_confirmation_courier_app.png',
    'assets/reference_screens/earnings_courier_app.png',
]
missing = [path for path in required if not (root / path).exists()]
if missing:
    print('Missing files:')
    for item in missing:
        print(f' - {item}')
    raise SystemExit(1)
print(f'Project validation OK: {len(required)} required files found.')
