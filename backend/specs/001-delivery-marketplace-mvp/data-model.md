# Data Model: Delivery Marketplace MVP

## Shared Rules

- All persisted entities use UUID primary keys.
- Tenant-scoped records include `tenant_id` unless they are global platform reference
  records.
- Important tables include `created_at` and `updated_at`.
- Concurrently updated records use optimistic locking with `version`.
- Frequent query fields receive indexes: `tenant_id`, `status`, `vendor_id`,
  `customer_id`, `courier_id`, and `created_at`.
- Sensitive data such as payment details, prescription metadata, delivery addresses,
  internal support notes, and delivery codes must not be logged.

## Entity: Tenant

Fields: `id`, `name`, `slug`, `status`, `default_currency`, `created_at`, `updated_at`.

Relationships: owns tenant-scoped users, vendors, couriers, zones, policies, orders,
payments, support tickets, and audit logs.

Validation: `slug` unique; status must be active before marketplace operations are
available.

## Entity: AppUserProfile

Fields: `id`, `tenant_id`, `keycloak_user_id`, `email`, `display_name`, `phone`,
`roles`, `status`, `created_at`, `updated_at`.

Relationships: may be customer, vendor staff/admin, courier, admin, operations, finance,
or support user.

Validation: user can access only records allowed by tenant and role.

## Entity: Vendor

Fields: `id`, `tenant_id`, `name`, `category_id`, `status`, `verification_status`,
`rating`, `estimated_delivery_minutes`, `available`, `location`, `service_zone_id`,
`created_at`, `updated_at`, `version`.

Relationships: has documents, opening hours, products, inventory, orders.

Validation: verified and available vendors can appear in customer search; fuel-station
vendors can sell only allowed convenience products.

## Entity: VendorDocument

Fields: `id`, `tenant_id`, `vendor_id`, `document_type`, `storage_key`, `metadata`,
`status`, `created_at`, `updated_at`.

Validation: stores metadata and object references only; document contents stay in local
object storage or future provider.

## Entity: VendorOpeningHour

Fields: `id`, `tenant_id`, `vendor_id`, `day_of_week`, `opens_at`, `closes_at`,
`closed`, `created_at`, `updated_at`.

Validation: opening interval required when not closed.

## Entity: Category

Fields: `id`, `tenant_id`, `name`, `vertical`, `parent_id`, `active`, `created_at`,
`updated_at`.

Validation: vertical must be one of restaurants, grocery, pharmacy, convenience, general
retail, electronics, florists, or fuel-station convenience.

## Entity: Product

Fields: `id`, `tenant_id`, `vendor_id`, `category_id`, `name`, `description`,
`status`, `requires_prescription_metadata`, `manual_validation_required`,
`prohibited_fuel`, `created_at`, `updated_at`, `version`.

Relationships: has one or more SKUs.

Validation: products flagged as fuel or prohibited fuel cannot be listed, added to cart,
or fulfilled.

## Entity: Sku

Fields: `id`, `tenant_id`, `product_id`, `sku_code`, `name`, `price`, `active`,
`created_at`, `updated_at`, `version`.

Relationships: has one inventory item per vendor stock location for MVP.

Validation: price must be non-negative; active SKU requires active product.

## Entity: InventoryItem

Fields: `id`, `tenant_id`, `vendor_id`, `sku_id`, `quantity_available`,
`quantity_reserved`, `status`, `created_at`, `updated_at`, `version`.

Validation: quantity cannot go below zero; reservation uses optimistic locking to
prevent overselling.

## Entity: CustomerAddress

Fields: `id`, `tenant_id`, `customer_id`, `label`, `recipient_name`, `phone`,
`street`, `number`, `district`, `city`, `region`, `postal_code`, `location`,
`delivery_instructions`, `default_address`, `created_at`, `updated_at`.

Validation: customer can manage only their own addresses; address must be in vendor zone
for delivery checkout.

## Entity: Cart

Fields: `id`, `tenant_id`, `customer_id`, `vendor_id`, `fulfillment_type`,
`customer_address_id`, `delivery_instructions`, `subtotal`, `fees`, `taxes`,
`discounts`, `total`, `status`, `created_at`, `updated_at`, `version`.

Relationships: has cart items.

Validation: one vendor per cart; active cart belongs to one customer; checkout validates
prices, stock, zone, and restrictions.

## Entity: CartItem

Fields: `id`, `tenant_id`, `cart_id`, `sku_id`, `product_name_snapshot`,
`sku_name_snapshot`, `unit_price_snapshot`, `quantity`, `created_at`, `updated_at`.

Validation: quantity must be positive; SKU must belong to the cart vendor.

## Entity: Order

Fields: `id`, `tenant_id`, `reference`, `customer_id`, `vendor_id`, `cart_id`,
`fulfillment_type`, `status`, `subtotal`, `fees`, `taxes`, `discounts`, `total`,
`customer_address_id`, `delivery_instructions`, `prescription_validation_status`,
`cancellation_reason`, `created_at`, `updated_at`, `version`.

Relationships: has order items, payment, refunds, delivery, audit events.

Validation: reference unique; state transitions validated by service logic.

Order states: `PENDING`, `PAYMENT_PENDING`, `PAYMENT_CONFIRMED`,
`ACCEPTED_BY_VENDOR`, `REJECTED_BY_VENDOR`, `PREPARING`, `READY_FOR_PICKUP`,
`DISPATCH_PENDING`, `ASSIGNED_TO_COURIER`, `PICKED_UP`, `DELIVERING`, `DELIVERED`,
`CANCELLED`, `REFUND_PENDING`, `REFUNDED`.

## Entity: OrderItem

Fields: `id`, `tenant_id`, `order_id`, `sku_id`, `product_name_snapshot`,
`sku_name_snapshot`, `unit_price_snapshot`, `quantity`, `line_total`, `created_at`.

Validation: immutable after order creation except cancellation/refund references.

## Entity: Payment

Fields: `id`, `tenant_id`, `order_id`, `method`, `status`, `amount`, `provider`,
`provider_reference`, `idempotency_key`, `confirmed_at`, `created_at`, `updated_at`,
`version`.

Validation: payment confirmation idempotency key must produce one final outcome.

Payment states: `INITIATED`, `PENDING_CONFIRMATION`, `CONFIRMED`, `FAILED`,
`CANCELLED`, `REFUND_PENDING`, `PARTIALLY_REFUNDED`, `REFUNDED`.

## Entity: Refund

Fields: `id`, `tenant_id`, `payment_id`, `order_id`, `amount`, `reason`, `status`,
`idempotency_key`, `requested_by_user_id`, `approved_by_user_id`, `created_at`,
`updated_at`, `version`.

Validation: total refund cannot exceed paid amount; partial refunds remain auditable.

## Entity: Courier

Fields: `id`, `tenant_id`, `user_profile_id`, `verification_status`, `available`,
`operating_zone_id`, `rating`, `created_at`, `updated_at`, `version`.

Relationships: has dispatch jobs and deliveries.

Validation: only verified available couriers can receive assignments.

## Entity: DispatchJob

Fields: `id`, `tenant_id`, `order_id`, `delivery_id`, `courier_id`, `status`,
`rejection_reason`, `assigned_at`, `accepted_at`, `created_at`, `updated_at`, `version`.

Validation: courier rejection makes job reassignable; operations can manually reassign.

## Entity: Delivery

Fields: `id`, `tenant_id`, `order_id`, `courier_id`, `status`,
`confirmation_code_hash`, `confirmation_attempts`, `proof_photo_storage_key`,
`cash_collected_amount`, `pickup_location`, `dropoff_location`, `created_at`,
`updated_at`, `version`.

Validation: delivered state requires correct 6-digit code; code must not be logged.

Delivery states: `DISPATCH_PENDING`, `ASSIGNED`, `ACCEPTED`, `REJECTED`,
`ARRIVED_AT_VENDOR`, `PICKED_UP`, `ON_ROUTE_TO_CUSTOMER`, `ARRIVED_AT_CUSTOMER`,
`DELIVERED`, `FAILED_DELIVERY`, `REASSIGNED`, `CANCELLED`.

## Entity: DeliveryEvent

Fields: `id`, `tenant_id`, `delivery_id`, `event_type`, `notes`, `actor_user_id`,
`created_at`.

Validation: captures lifecycle changes and failed code attempts for review.

## Entity: Notification

Fields: `id`, `tenant_id`, `recipient_user_id`, `recipient_role`, `type`, `title`,
`message`, `business_reference`, `read_at`, `created_at`.

Validation: recipients can read only their notifications.

## Entity: SupportTicket

Fields: `id`, `tenant_id`, `created_by_user_id`, `order_id`, `classification`,
`status`, `subject`, `public_description`, `internal_notes`, `assigned_to_user_id`,
`created_at`, `updated_at`, `version`.

Validation: internal notes visible only to authorized back-office users.

## Entity: AuditLog

Fields: `id`, `tenant_id`, `actor_user_id`, `action`, `target_type`, `target_id`,
`business_reference`, `result`, `created_at`.

Validation: sensitive admin, finance, operations, support, payment, refund, delivery,
and role actions create audit entries.

## Entity: Commission

Fields: `id`, `tenant_id`, `order_id`, `vendor_id`, `basis_amount`, `commission_rate`,
`commission_amount`, `status`, `created_at`, `updated_at`.

Validation: commission computed from completed or eligible orders according to tenant
policy.

## Entity: CashReconciliation

Fields: `id`, `tenant_id`, `courier_id`, `delivery_id`, `order_id`, `amount`, `status`,
`recorded_at`, `reconciled_at`, `created_at`, `updated_at`, `version`.

Validation: created when cash-on-delivery is enabled and courier records cash collected.

## State Transition Rules

- Orders move only through allowed lifecycle transitions; invalid transitions return a
  clear business error.
- Vendor rejection requires `rejection_reason` and triggers cancellation/refund handling.
- Courier rejection sets dispatch job to reassignable and creates an operational event.
- Payment confirmation and refund handling are idempotent by key/reference.
- Delivery completion requires the correct customer-provided 6-digit code.
- Pharmacy orders with required prescription metadata must pass manual validation before
  fulfillment.
- Fuel products cannot be listed, placed in cart, checked out, assigned, or delivered.
