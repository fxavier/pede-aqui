# Cart Management

## Purpose

Defines how customers add products to the cart, edit it, and how cart state is persisted in the Pede Aqui delivery web app.

## Requirements

### Requirement: Customer can add products to the cart
The system SHALL allow a customer to add a product to the cart by tapping "Adicionar" on a product card. The cart SHALL be scoped to a single vendor; adding a product from a different vendor SHALL prompt the customer to clear the existing cart first.

#### Scenario: Add first product to empty cart
- **WHEN** a customer taps "Adicionar" on a product
- **THEN** the product is added to the cart with quantity 1 and the cart icon shows a badge with item count

#### Scenario: Add same product again increments quantity
- **WHEN** a customer taps "Adicionar" on a product already in the cart
- **THEN** the product quantity increments by 1

#### Scenario: Add product from different vendor
- **WHEN** the cart contains items from vendor A and the customer taps "Adicionar" on a product from vendor B
- **THEN** the app shows a confirmation dialog asking to replace the current cart

#### Scenario: Customer confirms cart replacement
- **WHEN** the customer confirms replacing the cart
- **THEN** the cart is cleared and the new product is added

---

### Requirement: Customer can view and edit the cart
The system SHALL provide a cart drawer/page showing all items, quantities, unit prices, and a subtotal in MZN. The customer SHALL be able to increment, decrement, or remove items.

#### Scenario: Cart opens with current items
- **WHEN** a customer opens the cart
- **THEN** all cart items are displayed with name, quantity controls, unit price, and line total

#### Scenario: Decrement to zero removes item
- **WHEN** a customer decrements an item quantity from 1
- **THEN** the item is removed from the cart

#### Scenario: Empty cart state
- **WHEN** all items are removed
- **THEN** the cart displays "Seu carrinho está vazio" and a "Ver restaurantes" link

---

### Requirement: Cart state persists across page refresh
The system SHALL persist cart state to `localStorage` keyed by tenant ID. On app load, cart state SHALL be rehydrated from `localStorage` before rendering.

#### Scenario: Cart survives reload
- **WHEN** a customer refreshes the page while items are in the cart
- **THEN** the cart retains all items and quantities after reload

#### Scenario: Cart is isolated per tenant
- **WHEN** a customer switches tenant context (if applicable)
- **THEN** the cart loads the cart state for the new tenant (may be empty)

---

### Requirement: Cart requires authentication to proceed to checkout
The system SHALL show the cart to unauthenticated customers (read-only view) but the "Finalizar Pedido" button SHALL redirect to login when tapped by an unauthenticated user.

#### Scenario: Unauthenticated customer taps checkout
- **WHEN** an unauthenticated customer taps "Finalizar Pedido"
- **THEN** the app redirects to `/login?redirect=/checkout`
