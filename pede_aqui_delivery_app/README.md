# Pede Aqui Mobile — Flutter App

Flutter implementation generated from the provided ZIP screens.

## Stack

- Flutter + Dart
- BLoC/Cubit (`flutter_bloc`)
- GetIt dependency injection
- Provider for app-level settings
- Mock repositories now, API-ready repositories later
- Dio API client prepared for backend integration
- Reusable widgets and feature-oriented structure

## Included screens

All screens from the ZIP have corresponding Flutter routes:

| ZIP screen | Route | Flutter screen |
|---|---:|---|
| `pede_aqui_landing_page` | `/landing` | `LandingScreen` |
| `onboarding_pede_aqui` | `/onboarding` | `OnboardingScreen` |
| `login_register_pede_aqui` | `/auth` | `LoginRegisterScreen` |
| `home_pede_aqui` | `/home` | `HomeScreen` |
| `store_pede_aqui` | `/store` | `StoreScreen` |
| `cart_pede_aqui` | `/cart` | `CartScreen` |
| `checkout_pede_aqui` | `/checkout` | `CheckoutScreen` |
| `checkout_with_promotion_pede_aqui` | `/checkout-promo` | `CheckoutPromotionScreen` |
| `order_tracking_pede_aqui` | `/order-tracking` | `OrderTrackingScreen` |

Reference screenshots are included under `assets/reference_screens/`.

## Run

```bash
flutter pub get
flutter run
```

If your Flutter SDK says platform folders are missing, generate them once:

```bash
flutter create . --platforms=android,ios,web
flutter pub get
flutter run
```

## API integration later

Current data is served from mock repositories registered in `lib/core/di/service_locator.dart`.

For backend integration:

1. Set your API base URL in `lib/core/config/app_config.dart`.
2. Implement API repositories using `ApiClient` in `lib/core/network/api_client.dart`.
3. Replace mock bindings in `setupServiceLocator()`.

Example:

```dart
getIt.registerLazySingleton<CatalogRepository>(
  () => ApiCatalogRepository(getIt<ApiClient>()),
);
```

## Validation

```bash
python3 tool/validate_screens.py
```

This checks that all ZIP screens have route entries, Dart screen files and reference images.
