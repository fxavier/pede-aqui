# Research: Delivery Marketplace MVP

## Decision: Use a simple Spring Boot layered monolith for the MVP

**Rationale**: A single backend service with controllers, services, repositories,
entities, DTOs, mappers, config, exception, and security packages is the simplest
production-oriented structure for the MVP. It keeps transactions, tenant isolation,
state transitions, and operational flows easy to trace.

**Alternatives considered**: Microservices, DDD modules, Clean Architecture,
Hexagonal Architecture, ports/adapters, use case classes, command buses, CQRS, and
event sourcing. These were rejected because they add coordination and abstraction before
the MVP has proven scale or integration needs.

## Decision: Use Java 21, Spring Boot 3.x, Maven, and Spring Data JPA

**Rationale**: Java 21 and Spring Boot 3.x provide a current LTS runtime, mature web and
security support, strong JPA integration, validation, health checks, and broad team
familiarity. Maven is adequate and simple for a single backend service.

**Alternatives considered**: Gradle and non-Java stacks. Gradle is viable but not needed
for MVP complexity. Non-Java stacks would conflict with the requested backend stack.

## Decision: Use PostgreSQL with PostGIS and Flyway

**Rationale**: PostgreSQL is required by the constitution and is appropriate for orders,
payments, tenants, inventory, and audit records. PostGIS supports nearby vendor and zone
queries without adding a separate search/geospatial service. Flyway keeps schema changes
explicit, reviewable, and repeatable.

**Alternatives considered**: External search/geospatial services and manual schema
changes. These add operational complexity or reduce migration discipline.

## Decision: Use Keycloak JWTs through Spring Security OAuth2 Resource Server

**Rationale**: Keycloak centralizes identity and role assignment while the backend keeps
authorization checks simple at endpoints and in services. JWT resource server support is
standard in Spring Security and avoids storing passwords in the application database.

**Alternatives considered**: Application-managed passwords and custom auth flows. These
increase security risk and contradict the constitution.

## Decision: Enforce tenant isolation in services with explicit tenant IDs

**Rationale**: Service methods already contain business logic and transaction
boundaries, so tenant checks belong there alongside role rules. Tenant-scoped tables use
`tenant_id`, and queries include tenant filters for customer, vendor, courier,
operations, finance, and support data.

**Alternatives considered**: A generic tenancy framework or hidden repository wrappers.
These obscure access rules and add abstraction not needed for the MVP.

## Decision: Use local/mock payment provider for MVP

**Rationale**: The MVP must prove checkout, payment lifecycle, idempotent confirmation,
refunds, finance visibility, and cash reconciliation without external payment-provider
complexity. A local provider can still model duplicate callbacks and failure paths.

**Alternatives considered**: Immediate real payment integration. This is deferred until
the core order and payment flows are stable.

## Decision: Use persisted notifications and structured logs before real providers

**Rationale**: Database notification records and logs satisfy MVP visibility for
customers, vendors, couriers, operations, and admins. They keep the product testable and
allow SMS/email/push integration later without changing core business flows.

**Alternatives considered**: SMS, email, push, Kafka, or external notification systems in
MVP. These add integration and delivery concerns before the notification model is proven.

## Decision: Use status-based tracking for MVP

**Rationale**: Customers need current order and delivery status. Full GPS tracking is
explicitly out of scope. The data model keeps optional location fields for future GPS
updates without requiring live tracking now.

**Alternatives considered**: Live courier GPS, route optimization, advanced ETA, and map
provider integration. These are out of scope for MVP.

## Decision: Store delivery confirmation code as a hash when practical

**Rationale**: The customer must see the 6-digit code, and the courier must enter it.
Hashing the code at rest reduces exposure if delivery records are viewed incorrectly.
If a plaintext code is temporarily needed for display, access must be restricted and the
code must never be logged.

**Alternatives considered**: Store plaintext only. This is simpler but less safe; it is
acceptable only if protected carefully during early implementation and replaced with
hashing before production hardening.

## Decision: Use Redis only for clear cache or rate-limit needs

**Rationale**: Redis is useful for sensitive endpoint rate limiting and potentially hot
read caches, but core MVP correctness must not depend on caching. PostgreSQL remains the
source of truth.

**Alternatives considered**: Broad caching from the start. This risks stale data in
checkout, inventory, payment, and dispatch flows.

## Decision: Use Next.js backoffice with direct API service layer

**Rationale**: Admin, vendor, operations, finance, and support pages benefit from a
simple role-based web portal. Feature folders, shadcn/ui, Tailwind, and direct API
services keep the interface consistent without excessive global state.

**Alternatives considered**: Complex frontend domain layers or cross-platform web
abstractions. These are unnecessary for MVP backoffice screens.

## Decision: Use Flutter feature folders with BLoC for customer and courier screens

**Rationale**: Flutter supports pragmatic customer and courier mobile flows. BLoC gives
predictable state for auth, discovery, cart, checkout, orders, courier jobs, and delivery
confirmation without adopting Clean Architecture.

**Alternatives considered**: Clean Architecture in Flutter, deeply abstracted
repositories, and separate domain/use case layers. These are prohibited by the project
principles and unnecessary for the MVP.
