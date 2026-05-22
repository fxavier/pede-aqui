# Feature Specification: Delivery Marketplace MVP

**Feature Branch**: `001-delivery-marketplace-mvp`  
**Created**: 2026-05-10  
**Status**: Draft  
**Input**: User description: "Build the MVP of a multi-tenant delivery marketplace platform using a simple, pragmatic architecture."

## Constitution Alignment *(mandatory)*

- **Affected roles**: Customers, vendors, couriers, platform administrators, operations users, finance users, and support users.
- **Vertical/product scope**: Restaurants, grocery stores, pharmacies with prescription attachment metadata and manual validation, convenience stores, general retail, electronics, florists, and safe convenience products from fuel-station vendors.
- **Prohibited scope**: Fuel transport MUST NOT be included.
- **Security and tenant isolation**: Users MUST access only data and actions allowed by their role and tenant. Customer personal data, delivery addresses, delivery confirmation codes, payment records, prescription metadata, support notes, finance records, and audit history require restricted access.
- **Reliability rules**: Order, delivery, and payment state changes MUST be validated. Payment confirmation and refund processing MUST be idempotent. Delivery completion MUST require the customer-provided 6-digit code. Vendor rejection MUST include a reason. Courier rejection MUST make the delivery job available for reassignment. Cash-on-delivery reconciliation MUST be tracked when enabled.
- **Simplicity constraint**: The MVP MUST remain a simple, pragmatic product slice. It MUST NOT include multi-vendor carts, multi-drop routing, advanced AI/ML ETA, sponsored listings, loyalty tiers, drones, lockers, full ERP/accounting, automated prescription verification, advanced fraud scoring, advanced analytics warehouse, or complex microservices.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Customer Completes an Order (Priority: P1)

A customer registers or logs in, manages a delivery address, searches nearby vendors, filters results, browses a vendor catalog, builds a single-vendor cart, reviews fees, taxes, discounts, and total, places an order, pays, tracks status, receives notifications, views the 6-digit delivery code, and confirms receipt through the courier.

**Why this priority**: This is the primary marketplace value path and proves discovery, catalog, cart, checkout, payment, tracking, and delivery confirmation work together.

**Independent Test**: Can be tested by creating a customer, finding an available vendor, adding products from one vendor to a cart, checking out, confirming payment, tracking status changes, and completing delivery only with the correct 6-digit code.

**Acceptance Scenarios**:

1. **Given** an active customer, an in-zone address, and an available vendor with in-stock products, **When** the customer checks out, **Then** the order is created with a unique human-friendly reference, correct totals, payment status, delivery or pickup choice, and visible tracking status.
2. **Given** a customer has products from one vendor in the cart, **When** the customer attempts to add a product from another vendor, **Then** the system prevents the mixed-vendor cart and explains that MVP orders support one vendor only.
3. **Given** a courier reaches the customer, **When** the customer provides the correct 6-digit code and the courier enters it, **Then** the order and delivery are marked delivered and the customer can rate the vendor and courier.
4. **Given** a customer orders from a pharmacy, **When** prescription attachment metadata is required, **Then** the order cannot proceed to fulfillment until manual validation is completed.

---

### User Story 2 - Vendor Manages Catalog and Fulfillment (Priority: P2)

A vendor registers a business profile, submits verification document metadata, configures category, opening hours, availability, catalog categories, products, SKUs, prices, and stock, receives order notifications, accepts or rejects orders with a reason, updates preparation status, marks orders ready for pickup, and views basic sales and operational metrics.

**Why this priority**: Vendors must keep offerings accurate and process paid orders before customers and couriers can complete fulfillment.

**Independent Test**: Can be tested by creating a vendor, configuring catalog and stock, receiving an order, accepting it, moving it through preparation, and marking it ready for pickup.

**Acceptance Scenarios**:

1. **Given** a verified vendor with active products, **When** the vendor updates price, stock, or availability, **Then** customers see the latest purchasable catalog state before checkout.
2. **Given** a vendor receives a new paid order, **When** the vendor accepts it, **Then** the order moves to preparation and the customer receives an order status notification.
3. **Given** a vendor cannot fulfill an order, **When** the vendor rejects it, **Then** the vendor must provide a reason, the customer is notified, and the order follows the configured cancellation or refund path.

---

### User Story 3 - Courier Completes Delivery (Priority: P3)

A courier registers a profile, submits verification document metadata, sets availability, defines an operating zone, receives delivery assignments, accepts or rejects assignments, views pickup and drop-off details, marks arrival at vendor, marks pickup completed, marks arrival at customer, optionally records proof-of-delivery photo metadata, records cash collected when applicable, and completes delivery using the customer-provided 6-digit code.

**Why this priority**: Courier handoff closes the operational loop from prepared order to confirmed customer receipt.

**Independent Test**: Can be tested by assigning a ready order to an available courier, walking the delivery through pickup and drop-off statuses, and verifying completion fails without the correct code and succeeds with it.

**Acceptance Scenarios**:

1. **Given** a ready order in a courier's zone, **When** the courier accepts the assignment, **Then** the delivery moves to accepted and the courier sees only the pickup, drop-off, and contact details needed for the job.
2. **Given** a courier rejects an assignment, **When** the rejection is recorded, **Then** the delivery becomes available for reassignment and operations users can monitor the event.
3. **Given** cash-on-delivery is enabled for an order, **When** the courier completes delivery, **Then** the collected cash amount is recorded for finance reconciliation.

---

### User Story 4 - Admin and Operations Manage Marketplace Operations (Priority: P4)

Platform administrators and operations users manage tenants, users, roles, platform categories, vendor verification, courier verification, zones, fees, commissions, taxes, cancellation policies, order monitoring, delivery reassignment, support visibility, audit logs, and an operational dashboard.

**Why this priority**: The marketplace needs controlled operational intervention for verification, configuration, exceptions, and daily monitoring.

**Independent Test**: Can be tested by creating a tenant, assigning roles, verifying a vendor and courier, configuring zones and fees, monitoring order status, manually reassigning a delivery, and confirming audit events are visible.

**Acceptance Scenarios**:

1. **Given** a platform administrator creates or updates tenant and role configuration, **When** users access the system, **Then** capabilities and data visibility follow the configured role and tenant boundaries.
2. **Given** an active order has a failed delivery or rejected courier assignment, **When** an operations user reviews it, **Then** they can reassign the delivery job and see the event in the operational dashboard.
3. **Given** a sensitive admin or operations action occurs, **When** audit logs are reviewed, **Then** the actor, action, target, timestamp, and result are visible to authorized users.

---

### User Story 5 - Finance Monitors Money Movement (Priority: P5)

Finance users view payment transactions, commissions, refunds, cash-on-delivery reconciliation, vendor payout status, and export basic financial reports.

**Why this priority**: The MVP must make marketplace money movement visible and auditable without building a full accounting system.

**Independent Test**: Can be tested by creating paid, refunded, commission-bearing, and cash-on-delivery orders, then verifying finance users can view and export the related records.

**Acceptance Scenarios**:

1. **Given** orders have completed payments and commissions, **When** a finance user opens the dashboard, **Then** transactions, commissions, refunds, and cash reconciliation totals are visible.
2. **Given** a refund is total or partial, **When** the refund status changes, **Then** finance users see one auditable final result for each refund request.
3. **Given** vendor payout status is reviewed, **When** finance exports a basic report, **Then** the report includes the relevant transactions, commissions, refunds, cash reconciliation, and payout status.

---

### User Story 6 - Support Resolves Tickets (Priority: P6)

Support users view support tickets, link tickets to orders, classify incidents, update ticket status, add internal notes, and resolve tickets.

**Why this priority**: Customers, vendors, and couriers need a simple support path for delivery, payment, catalog, and operational issues.

**Independent Test**: Can be tested by opening a customer ticket, linking it to an order, classifying it, adding internal notes, changing status, and resolving it with role-limited visibility.

**Acceptance Scenarios**:

1. **Given** a customer opens a support ticket for an order, **When** a support user links the ticket to that order, **Then** the ticket shows the order reference, classification, status, and allowed notes.
2. **Given** support adds an internal note, **When** a customer views their ticket, **Then** the internal note remains hidden from the customer.
3. **Given** a ticket is resolved, **When** the status changes, **Then** the customer sees the resolution status and authorized back-office users see the audit trail.

---

### Edge Cases

- A customer tries to add products from multiple vendors to one cart.
- A vendor becomes unavailable after a customer starts checkout.
- A product becomes out of stock during concurrent checkout attempts.
- A customer address is outside the selected vendor's delivery zone.
- A fuel-station vendor attempts to list fuel or a customer attempts to buy fuel.
- A pharmacy order is missing required prescription attachment metadata.
- Manual pharmacy validation rejects an order before fulfillment.
- Payment confirmation, refund request, cancellation request, or delivery completion is retried or received more than once.
- A vendor rejects an order without a reason.
- A courier rejects an assignment or fails delivery after pickup.
- A courier enters an incorrect delivery confirmation code.
- A user attempts to access data from another tenant or outside their role.
- A support user attempts to expose internal notes to a customer.
- A finance user reviews cash-on-delivery records before the courier has reconciled collected cash.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support Customer, Vendor, Courier, Platform Admin, Operations user, Finance user, and Support user roles.
- **FR-002**: System MUST enforce tenant isolation and role-based access for all customer, vendor, courier, admin, operations, finance, and support data.
- **FR-003**: System MUST allow customers to register, log in, manage profile details, manage delivery addresses, view order history, rate vendors and couriers, and open support tickets.
- **FR-004**: Customers MUST be able to search nearby vendors and filter vendors by category, distance, rating, delivery time, and availability.
- **FR-005**: Customers MUST be able to browse vendor catalogs across supported verticals: restaurants, grocery stores, pharmacies, convenience stores, general retail, electronics, florists, and fuel-station convenience products only.
- **FR-006**: System MUST block fuel from listing, cart, checkout, order fulfillment, and delivery while allowing safe convenience products from fuel-station vendors.
- **FR-007**: Customers MUST be able to create and update a cart containing products from one vendor only.
- **FR-008**: System MUST prevent a cart from containing products from multiple vendors.
- **FR-009**: Customers MUST be able to choose delivery or pickup, add delivery instructions, and view fees, taxes, discounts, and total before checkout.
- **FR-010**: System MUST validate vendor availability, product availability, stock, delivery zone, pharmacy requirements, pricing, fees, taxes, discounts, and customer permissions before order placement.
- **FR-011**: Each order MUST have a unique human-friendly order reference.
- **FR-012**: Each delivery order MUST have a 6-digit confirmation code that is visible to the customer and hidden from unauthorized users.
- **FR-013**: Couriers MUST enter the correct 6-digit confirmation code before the system marks a delivery as delivered.
- **FR-014**: System MUST support the order lifecycle states PENDING, PAYMENT_PENDING, PAYMENT_CONFIRMED, ACCEPTED_BY_VENDOR, REJECTED_BY_VENDOR, PREPARING, READY_FOR_PICKUP, DISPATCH_PENDING, ASSIGNED_TO_COURIER, PICKED_UP, DELIVERING, DELIVERED, CANCELLED, REFUND_PENDING, and REFUNDED.
- **FR-015**: System MUST validate all order state changes and reject invalid transitions with a clear reason.
- **FR-016**: System MUST support the delivery lifecycle states DISPATCH_PENDING, ASSIGNED, ACCEPTED, REJECTED, ARRIVED_AT_VENDOR, PICKED_UP, ON_ROUTE_TO_CUSTOMER, ARRIVED_AT_CUSTOMER, DELIVERED, FAILED_DELIVERY, REASSIGNED, and CANCELLED.
- **FR-017**: System MUST support the payment lifecycle states INITIATED, PENDING_CONFIRMATION, CONFIRMED, FAILED, CANCELLED, REFUND_PENDING, PARTIALLY_REFUNDED, and REFUNDED.
- **FR-018**: Payment confirmation MUST be idempotent so duplicate confirmations produce one final payment outcome.
- **FR-019**: Refunds MUST support total and partial refund outcomes and remain auditable.
- **FR-020**: Customers MUST be able to cancel orders according to the configured cancellation policy.
- **FR-021**: Pharmacy orders MUST capture prescription attachment metadata and require manual validation before fulfillment.
- **FR-022**: Vendors MUST be able to register a business profile, submit verification document metadata, configure category, opening hours, availability, catalog categories, products, SKUs, prices, and stock.
- **FR-023**: Vendors MUST receive new order notifications and be able to accept orders, reject orders with a reason, update preparation status, and mark orders ready for pickup.
- **FR-024**: Vendors MUST be able to view basic sales and operational metrics including sales summary, orders by status, top products, and rejected orders.
- **FR-025**: Couriers MUST be able to register a profile, submit verification document metadata, set online or offline availability, define operating zone, receive assignments, accept or reject assignments, and view pickup and drop-off details.
- **FR-026**: Courier rejection MUST make the delivery job available for reassignment.
- **FR-027**: Couriers MUST be able to mark arrival at vendor, pickup completed, arrival at customer, failed delivery, and delivered using the 6-digit code.
- **FR-028**: Couriers MUST be able to optionally attach proof-of-delivery photo metadata and record cash collected when cash-on-delivery is enabled.
- **FR-029**: Couriers MUST be able to view basic earnings summary, completed deliveries, and failed deliveries.
- **FR-030**: Platform administrators MUST be able to manage tenants, users and roles, platform categories, vendor verification, courier verification, delivery zones, fees, commissions, taxes, cancellation policies, support tickets, and audit logs.
- **FR-031**: Operations users MUST be able to monitor orders, see important operational events, manually reassign delivery jobs, and view an operational dashboard.
- **FR-032**: Finance users MUST be able to view payment transactions, commissions, refunds, cash-on-delivery reconciliation, vendor payout status, and export basic financial reports.
- **FR-033**: Support users MUST be able to view support tickets, link tickets to orders, classify incidents, update ticket status, add internal notes, and resolve tickets.
- **FR-034**: Internal support notes MUST be visible only to authorized back-office users and hidden from customers, vendors, and couriers unless explicitly shared through a public response.
- **FR-035**: All sensitive admin, finance, operations, and support actions MUST be audited.
- **FR-036**: Customers MUST receive order status notifications; vendors MUST receive new order notifications; couriers MUST receive assignment notifications; admin and operations users MUST see important operational events.
- **FR-037**: The MVP MUST support simple persisted notifications and local operational logging before external SMS, email, or push providers are added.
- **FR-038**: Customers MUST see current order and delivery status using status-based tracking; full live GPS tracking is not required in the MVP.
- **FR-039**: The MVP MUST keep enough delivery location and status information to allow future GPS updates without requiring live GPS in the MVP.
- **FR-040**: Admin dashboard MUST show orders by status, active vendors, active couriers, cancellations, and failed deliveries.
- **FR-041**: Vendor dashboard MUST show sales summary, orders by status, top products, and rejected orders.
- **FR-042**: Finance dashboard MUST show transactions, commissions, refunds, and cash-on-delivery reconciliation.
- **FR-043**: Courier dashboard MUST show completed deliveries, failed deliveries, and earnings summary.
- **FR-044**: System MUST return user-friendly validation errors for invalid checkout, unauthorized access, invalid state transitions, unavailable products, missing prescription metadata, and incorrect delivery codes.
- **FR-045**: System MUST document setup, running, testing, configuration, and environment variables for new developers and operators.

### Non-Functional Requirements

- **NFR-001**: The MVP MUST remain simple and maintainable, avoiding unnecessary architectural complexity and speculative abstractions.
- **NFR-002**: Important business flows MUST be traceable through structured operational records and correlation identifiers.
- **NFR-003**: Sensitive data, including secrets, tokens, payment data, prescription files or metadata, delivery codes, delivery addresses, internal support notes, and sensitive personal data, MUST NOT be exposed in logs or unauthorized views.
- **NFR-004**: Sensitive actions such as login, checkout, payment confirmation, refund approval, delivery code attempts, role changes, and support-note access MUST have abuse prevention controls.
- **NFR-005**: The MVP MUST include understandable tests for role access, tenant isolation, cart rules, order state transitions, payment idempotency, refund idempotency, stock concurrency, pharmacy validation, fuel blocking, delivery code completion, cash reconciliation, dashboards, and support-ticket visibility.
- **NFR-006**: Public-facing product behavior and complex business rules MUST have concise explanatory documentation for new developers and operators.

### Key Entities *(include if feature involves data)*

- **Tenant**: A marketplace operating context that owns users, vendors, couriers, zones, policies, and operational data boundaries.
- **User**: A person with login identity, profile details, one or more roles, tenant membership, and permissions.
- **Customer**: A buyer with profile information, delivery addresses, carts, orders, ratings, and support tickets.
- **Vendor**: A seller with business profile, verification metadata, category, operating hours, availability, service zones, catalog, stock, orders, and metrics.
- **Courier**: A delivery worker with profile, verification metadata, availability, operating zone, delivery assignments, delivery history, and earnings summary.
- **Platform Category**: A marketplace category or vertical used to classify vendors and products.
- **Product**: A catalog item with vendor, category, name, description, price, SKU details, stock, availability, restrictions, and prohibited-fuel status.
- **Prescription Metadata**: Attachment-related metadata and manual validation status required for pharmacy orders where applicable.
- **Cart**: A customer purchase draft for one vendor, including items, quantities, fulfillment choice, address, instructions, fees, taxes, discounts, and total.
- **Order**: A confirmed purchase with reference, customer, vendor, items, totals, payment status, order status, fulfillment choice, delivery details, cancellation, refund, and audit history.
- **Payment Transaction**: A payment or cash record with lifecycle status, amount, method, idempotency reference, refund links, commission inputs, and reconciliation status.
- **Refund**: A total or partial reversal record with status, reason, amount, actor, and audit history.
- **Delivery Job**: A courier assignment with lifecycle status, pickup and drop-off details, confirmation code state, proof metadata, cash collection, and reassignment history.
- **Zone**: A service or operating area used for vendor availability, courier eligibility, delivery fees, and operations monitoring.
- **Fee and Commission Policy**: Tenant-level rules for delivery fees, taxes, discounts, commissions, cancellation policy, and cash-on-delivery availability.
- **Notification**: A persisted message for customers, vendors, couriers, admins, operations users, finance users, or support users.
- **Support Ticket**: A customer, vendor, or courier issue record with linked order, classification, status, public updates, internal notes, and resolution.
- **Audit Event**: A record of sensitive or important actions with actor, tenant, target, action, timestamp, result, and relevant business reference.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: At least 90% of acceptance-test customers can search for a vendor, browse products, create a cart, checkout, pay, track the order, and complete delivery confirmation without staff assistance.
- **SC-002**: Customers can complete a standard one-vendor checkout from vendor selection to order confirmation in under 3 minutes during acceptance testing.
- **SC-003**: In 100% of tested mixed-vendor cart attempts, the system prevents checkout and explains the one-vendor-per-order rule.
- **SC-004**: In 100% of tested fuel listing and checkout attempts, fuel transport is blocked while allowed fuel-station convenience products remain purchasable.
- **SC-005**: In 100% of tested pharmacy fulfillment attempts requiring prescription metadata, fulfillment is blocked until manual validation is complete.
- **SC-006**: Duplicate payment confirmations, refund requests, and delivery completion attempts result in one final business outcome in 100% of duplicate-event tests.
- **SC-007**: Unauthorized cross-tenant and out-of-role access attempts are blocked in 100% of role and tenant isolation acceptance tests.
- **SC-008**: Concurrent checkout tests for limited inventory never produce confirmed orders exceeding available stock.
- **SC-009**: Couriers cannot mark a delivery delivered without the correct 6-digit confirmation code in 100% of delivery completion tests.
- **SC-010**: Vendors can create catalog items, update stock, accept an order, reject an order with a reason, and mark an order ready for pickup in under 5 minutes during acceptance testing.
- **SC-011**: Operations users can identify and reassign a failed or rejected delivery job in under 5 minutes during acceptance testing.
- **SC-012**: Finance users can view transactions, commissions, refunds, cash-on-delivery reconciliation, and export a basic financial report in under 5 minutes during acceptance testing.
- **SC-013**: Support users can link a ticket to an order, classify it, add an internal note, update status, and resolve it in under 5 minutes during acceptance testing.
- **SC-014**: Admin, vendor, finance, and courier dashboards show their required MVP metrics for a seeded test marketplace with active orders, cancellations, refunds, failed deliveries, and completed deliveries.

## Assumptions

- The MVP is one deployable product experience with role-specific customer, vendor, courier, admin, operations, finance, and support workflows.
- Authentication and identity setup will be available to support role-based access in acceptance testing.
- External SMS, email, push, payment, map, and accounting integrations can be represented by persisted records and operational logs in the MVP unless a later plan requires provider integration.
- Cash-on-delivery is optional and enabled only where tenant policy allows it.
- Pharmacy support includes prescription attachment metadata and manual validation status; automated prescription verification is out of scope.
- Customer tracking is status-based for MVP; full live GPS tracking, route optimization, and advanced ETA are out of scope.
- Financial reporting is limited to basic operational exports and does not replace a full ERP or accounting system.
- Ratings are simple post-order vendor and courier ratings without loyalty, advanced analytics, or fraud scoring.
