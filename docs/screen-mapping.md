# Screen Mapping: Stitch Designs ↔ Implemented Screens ↔ Backend APIs

## pede_aqui_delivery_app (Customer/Delivery App)

| # | Stitch Design File | Route | Flutter Screen | Backend APIs | Status |
|---|---|---|---|---|---|
| 1 | `pede_aqui_landing_page/` | `/landing` | `LandingScreen` | None (static marketing) | ✅ Matches design |
| 2 | `onboarding_pede_aqui/` | `/onboarding` | `OnboardingScreen` | None (static onboarding) | ✅ Matches design |
| 3 | `login_register_pede_aqui/` | `/auth` | `LoginRegisterScreen` | Keycloak JWT (via backend `/api/v1/me`) | 🔄 Needs API connection |
| 4 | `home_pede_aqui/` | `/home` | `HomeScreen` | `GET /api/v1/search/vendors`, `GET /api/v1/vendors` | 🔄 Needs API connection |
| 5 | `store_pede_aqui/` | `/store` | `StoreScreen` | `GET /api/v1/catalog/vendors/{vendorId}/products` | 🔄 Needs API connection |
| 6 | `cart_pede_aqui/` | `/cart` | `CartScreen` | `POST /api/v1/customers/{customerId}/cart/items`, `GET /api/v1/customers/{customerId}/cart/pricing` | 🔄 Needs API connection |
| 7 | `checkout_pede_aqui/` | `/checkout` | `CheckoutScreen` | `POST /api/v1/checkout`, `POST /api/v1/payments/{paymentId}/confirm` | 🔄 Needs API connection |
| 8 | `checkout_with_promotion_pede_aqui/` | `/checkout-promo` | `CheckoutPromotionScreen` | `POST /api/v1/checkout`, promo endpoints | 🔄 Needs API connection |
| 9 | `order_tracking_pede_aqui/` | `/order-tracking` | `OrderTrackingScreen` | `GET /api/v1/orders/{orderId}/tracking` | 🔄 Needs API connection |

## pede_aqui_courier_app (Courier/Driver App)

| # | Stitch Design File | Route | Flutter Screen | Backend APIs | Status |
|---|---|---|---|---|---|
| 1 | `job_dashboard_courier_app/` | `/app` | `ShellScreen` / `HomeDashboardScreen` | `GET /api/v1/couriers/me`, `GET /api/v1/dispatch-jobs`, `PATCH /api/v1/couriers/me/availability`, `GET /api/v1/dashboards/courier` | ✅ Architecture aligned, 🔄 needs API connection |
| 2 | `active_delivery_detail_courier_app/` | `/delivery-detail` | `DeliveryDetailScreen` | `GET /api/v1/deliveries/{deliveryId}`, `PATCH /api/v1/deliveries/{deliveryId}/status` | ✅ Architecture aligned, 🔄 needs API connection |
| 3 | `delivery_confirmation_courier_app/` | `/confirm-delivery` | `DeliveryConfirmationScreen` | `POST /api/v1/deliveries/{deliveryId}/complete` | ✅ Architecture aligned, 🔄 needs API connection |
| 4 | `earnings_courier_app/` | `earnings tab` | `EarningsScreen` | `GET /api/v1/couriers/me/earnings-summary` | ✅ Architecture aligned, 🔄 needs API connection |

## pede-aqui-backoffice (Admin/Backoffice Web App)

| # | Stitch Design File | Route | Next.js Page | Backend APIs | Status |
|---|---|---|---|---|---|---|
| 1 | `admin_central_pede_aqui/` | `/admin` | `admin/page.tsx` | `GET /api/v1/dashboards/admin`, `GET /api/v1/admin/tenants` | ✅ Real UI with KPI cards, orders table, status distribution, cancellations/failures |
| — | (Stitch import) | `/screens/admin_central_pede_aqui` | `screens/admin_central_pede_aqui/page.tsx` | (preserved for reference) | 🔄 Renders HTML from Stitch |
| 2 | `vendor_portal_pede_aqui/` | `/vendors` | `vendors/page.tsx` | `GET /api/v1/dashboards/vendor`, `GET /api/v1/vendor/orders` | ✅ Real UI with KPI cards, order tabs, top products |
| — | (Stitch import) | `/screens/vendor_portal_pede_aqui` | `screens/vendor_portal_pede_aqui/page.tsx` | (preserved for reference) | 🔄 Renders HTML from Stitch |
| 3 | `live_order_management_vendor_portal/` | auto-routed | `[slug]/page.tsx` | `GET /api/v1/vendor/orders`, `PATCH /api/v1/vendor/orders/{orderId}/accept|reject|preparing|ready-for-pickup` | 🔄 Renders HTML |
| 4 | `courier_profile_admin_central/` | auto-routed | `[slug]/page.tsx` | `GET /api/v1/couriers/me`, `GET /api/v1/dispatch-jobs` | 🔄 Renders HTML |
| 5 | `finance_settlements_backoffice/` | auto-routed | `[slug]/page.tsx` | `GET /api/v1/finance/*` | 🔄 Renders HTML |
| 6 | `support_ticketing_help_desk/` | auto-routed | `[slug]/page.tsx` | `GET /api/v1/support/tickets`, `PATCH /api/v1/support/tickets/*` | 🔄 Renders HTML |
| 7 | `system_audit_logs_admin_central/` | auto-routed | `[slug]/page.tsx` | `GET /api/v1/admin/audit` | 🔄 Renders HTML |
| 8 | `roles_permissions_admin_central/` | auto-routed | `[slug]/page.tsx` | `POST /api/v1/tenants`, role management | 🔄 Renders HTML |
| 9+ | All other screens | `/screens/[slug]` | `[slug]/page.tsx` via `ImportedScreenView` | Various | 🔄 Renders HTML from Stitch |

**Legend**: ✅ = Completed, 🔄 = In progress/in need of work, ❌ = Missing, ✨ = New (this session)

## Key Observations

### Architecture Alignment

| Aspect | pede_aqui_delivery_app | pede_aqui_courier_app | pede-aqui-backoffice |
|---|---|---|---|
| State Management | Cubit + Bloc | Cubit + Bloc | Redux Toolkit + React Query |
| API Client | Dio-based | Dio-based | Fetch-based |
| DI | GetIt | GetIt | N/A (React hooks) |
| Repository Pattern | ✅ Abstract + Mock | ✅ Abstract + Mock | ✅ Mock fallback in pages |
| Theme | Material 3 custom | Material 3 custom | Tailwind CSS custom |
| Localization | ✅ pt_MZ locale (fixed) | ✅ pt_MZ locale | ✅ pt_PT in layout |
| Tests | Minimal | None | None |

### Color Alignment

The Stitch designs use Material Design 3 colors with:
- **Primary**: `#B02700` (deep coral-red) — NOT Facebook blue `#1877F2`
- **Secondary**: `#006D3F` (forest green)
- **Tertiary**: `#005F9E` (blue)
- **Background**: `#FFF8F6` (warm off-white)

Note: The requirement says "Use Facebook blue as main primary color where the designs use it." The Stitch designs do NOT use Facebook blue; they use coral-red `#B02700`. We should follow the Stitch designs.
