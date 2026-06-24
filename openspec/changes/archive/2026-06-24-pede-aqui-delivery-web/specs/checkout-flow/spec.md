## ADDED Requirements

### Requirement: Customer can enter a delivery address
The system SHALL display an address form at the start of checkout with fields: street, number, neighbourhood, city, and optional delivery notes. All fields except notes are required.

#### Scenario: Address form validation
- **WHEN** a customer submits the checkout form with missing required fields
- **THEN** the app highlights the missing fields with inline validation messages

#### Scenario: Valid address entered
- **WHEN** a customer fills all required fields and proceeds
- **THEN** the app advances to the order summary step

### Requirement: Customer can review the order before placing it
The system SHALL display an order summary showing vendor name, all cart items with quantities and prices, delivery address, and total amount in MZN before the customer confirms.

#### Scenario: Order summary shown
- **WHEN** a customer completes the address step
- **THEN** the app shows the full order summary with items, address, and total

### Requirement: Customer can place an order
The system SHALL submit the order via `POST /api/v1/checkout` with an idempotency key (UUID generated client-side per checkout session), the cart items, delivery address, and mock payment method. On success, the cart SHALL be cleared and the customer redirected to the order confirmation page.

#### Scenario: Successful order placement
- **WHEN** a customer confirms the order
- **THEN** the app calls the checkout API, clears the cart, and redirects to `/orders/{orderId}/confirmation`

#### Scenario: Duplicate submission (same idempotency key)
- **WHEN** the customer taps confirm twice (double-click / slow network)
- **THEN** only one order is created (backend idempotency handles deduplication) and the app navigates to the confirmation page

#### Scenario: Checkout API error
- **WHEN** the checkout API returns an error
- **THEN** the app displays an error message and allows the customer to retry without generating a new idempotency key

#### Scenario: Cart is empty at checkout
- **WHEN** a customer navigates directly to `/checkout` with an empty cart
- **THEN** the app redirects to the home page

### Requirement: Customer sees an order confirmation screen
The system SHALL display a confirmation page at `/orders/{orderId}/confirmation` showing order ID, vendor name, item list, total, and estimated preparation message. A "Ver meus pedidos" link navigates to order history.

#### Scenario: Confirmation page renders
- **WHEN** the customer is redirected to the confirmation page after checkout
- **THEN** the order details are displayed with a success message

#### Scenario: Direct navigation to confirmation page
- **WHEN** a customer navigates directly to `/orders/{orderId}/confirmation`
- **THEN** the app fetches the order from the API and renders it if found
