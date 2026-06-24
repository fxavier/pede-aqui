# Order Tracking

## Purpose

Defines how authenticated customers view their order history and track the real-time status of active deliveries in the Pede Aqui delivery web app.

## Requirements

### Requirement: Customer can view order history
The system SHALL display all past and active orders for the authenticated customer, fetched from `GET /api/v1/orders/my`, ordered by creation date descending. Each order card SHALL show order ID, vendor name, status label, total, and creation date.

#### Scenario: Order list renders
- **WHEN** an authenticated customer navigates to `/orders`
- **THEN** a list of their orders is displayed, newest first

#### Scenario: No orders yet
- **WHEN** the customer has no orders
- **THEN** the app displays "Você ainda não fez nenhum pedido" with a "Pedir agora" link

#### Scenario: Order status labels
- **WHEN** an order has status `PAYMENT_CONFIRMED`
- **THEN** the app displays the label "Aguardando confirmação do restaurante"

---

### Requirement: Customer can view order detail and track status
The system SHALL display full order details at `/orders/{orderId}` including items, delivery address, status, and a status timeline. For active orders (status not DELIVERED or FAILED_DELIVERY) the app SHALL poll `GET /api/v1/orders/{orderId}` every 30 seconds to refresh status.

#### Scenario: Order detail page renders
- **WHEN** a customer navigates to `/orders/{orderId}`
- **THEN** the app displays all order details and a status timeline reflecting the current state

#### Scenario: Active order status auto-refreshes
- **WHEN** an order is in an active status (not DELIVERED or FAILED_DELIVERY)
- **THEN** the app polls the order API every 30 seconds and updates the status timeline without a full page reload

#### Scenario: Completed order stops polling
- **WHEN** the polled status changes to DELIVERED or FAILED_DELIVERY
- **THEN** the app stops polling and displays the final status

#### Scenario: Order not found
- **WHEN** the API returns 404 for the requested order ID
- **THEN** the app displays "Pedido não encontrado" with a link back to order history

---

### Requirement: Status labels map delivery state machine to human-readable Portuguese
The system SHALL map all backend delivery status values to Portuguese labels visible on the order detail page.

| Backend Status | Label Displayed |
|---|---|
| PAYMENT_CONFIRMED | Aguardando restaurante |
| ACCEPTED_BY_VENDOR | Confirmado pelo restaurante |
| PREPARING | Em preparação |
| READY_FOR_PICKUP | Pronto para retirada |
| ACCEPTED (courier) | Estafeta a caminho do restaurante |
| ARRIVED_AT_VENDOR | Estafeta no restaurante |
| PICKED_UP | Pedido retirado |
| ON_ROUTE_TO_CUSTOMER | A caminho de si |
| ARRIVED_AT_CUSTOMER | Estafeta chegou |
| DELIVERED | Entregue |
| FAILED_DELIVERY | Entrega falhada |

#### Scenario: Status label rendered correctly
- **WHEN** an order has status `ON_ROUTE_TO_CUSTOMER`
- **THEN** the app displays "A caminho de si" in the status timeline
