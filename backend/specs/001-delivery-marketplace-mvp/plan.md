# Implementation Plan: Delivery Marketplace MVP

**Branch**: `001-delivery-marketplace-mvp` | **Date**: 2026-05-10 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-delivery-marketplace-mvp/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Build a production-oriented MVP for a multi-tenant delivery marketplace covering
customer discovery, single-vendor cart, checkout, mock payment confirmation, vendor
fulfillment, courier dispatch, delivery confirmation, notifications, support, finance,
and dashboards. The implementation uses a simple layered Spring Boot backend, a
role-based Next.js backoffice, and Flutter customer/courier screens. It intentionally
avoids DDD, Clean Architecture, Hexagonal Architecture, CQRS, event sourcing, command
buses, ports/adapters, speculative interfaces, Kafka, and complex orchestration.

## Technical Context

**Language/Version**: Java 21 with Spring Boot 3.x; TypeScript with Next.js; Flutter stable/Dart 3.x  
**Primary Dependencies**: Spring Web, Spring Security, OAuth2 Resource Server, Spring Data JPA, PostgreSQL JDBC, PostGIS, Flyway, OpenAPI/Swagger, MapStruct or simple mapper classes, JUnit 5, Testcontainers, Maven, Next.js, Typescript, redux toolkit, react queries, Tailwind CSS, shadcn/ui, Flutter BLoC  
**Storage**: PostgreSQL with Flyway migrations; PostGIS for vendor location and zone queries; Redis only for clearly useful cache/rate-limit cases; MinIO for local prescription/proof metadata file storage if file objects are needed  
**Testing**: JUnit 5 unit tests, Spring Boot integration/API tests, Testcontainers for PostgreSQL/PostGIS and optional Redis/Keycloak flows, frontend component/page tests where practical, Flutter widget/BLoC tests for critical flows  
**Target Platform**: Backend service, web backoffice, Flutter customer app, Flutter courier app, Docker Compose local infrastructure  
**Project Type**: Mixed vertical slice with backend-first implementation, web operations portal, and mobile customer/courier MVP screens  
**Performance Goals**: Customer vendor search and catalog browsing feel responsive under MVP load; 95% of status updates visible to relevant users within 10 seconds in acceptance tests; standard checkout completes in under 3 minutes  
**Constraints**: One vendor per order; status-based tracking instead of live GPS; local/mock payment provider; persisted notifications plus logs before SMS/email/push; no Kafka in MVP; no complex microservices; no prohibited architecture patterns  
**Scale/Scope**: MVP supports seeded multi-tenant marketplace data, all specified roles, required verticals, core order/payment/delivery lifecycles, dashboards, support tickets, finance views, and acceptance-test flows rather than large-scale marketplace optimization

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Simplicity First**: PASS. The plan uses direct controllers, services,
  repositories, entities, DTOs, mappers, config, exception, and security packages. It
  explicitly excludes DDD, Clean Architecture, Hexagonal Architecture, ports/adapters,
  use case classes, command/query buses, CQRS, event sourcing, Kafka, and speculative
  interfaces.
- **Layered Backend**: PASS. Backend work is organized under `com.delivery` by business
  feature packages with simple layers. Services own transactions and business rules.
  Repositories extend Spring Data JPA directly.
- **Security**: PASS. Keycloak JWT, OAuth2 resource server, endpoint authorization,
  tenant isolation in services, input validation, audit logs, and sensitive-data logging
  restrictions are planned. Rate limiting is planned for sensitive endpoints during
  hardening.
- **Database Consistency**: PASS. PostgreSQL, Flyway, UUID primary keys, `tenant_id`,
  timestamps, constraints, indexes, and optimistic locking are included. PostGIS is used
  only for geospatial vendor/zone queries.
- **Reliability**: PASS. Order, delivery, and payment state transitions are explicit.
  Payment confirmation is idempotent. Refunds are auditable. Delivery completion
  requires a 6-digit code. Cash-on-delivery reconciliation is tracked.
- **Observability**: PASS. Structured logs, correlation IDs, health checks, metrics, and
  targeted business-flow logs are included without over-instrumenting simple methods.
- **Testing**: PASS. Unit tests cover service rules, integration tests cover
  repositories and critical flows, and API tests cover checkout, payment confirmation,
  order lifecycle, delivery confirmation, tenant isolation, and authorization. Coverage
  target is 80%.
- **Vertical Slice Delivery**: PASS. Phases progress from local environment and backend
  skeleton through working customer checkout, vendor fulfillment, courier delivery,
  support/finance/dashboards, web portal, mobile screens, tests, docs, and hardening.

Post-design re-check: PASS. The generated research, data model, contracts, and
quickstart preserve simple layered implementation and do not introduce prohibited
patterns or unresolved clarifications.

## Project Structure

### Documentation (this feature)

```text
specs/001-delivery-marketplace-mvp/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── openapi.yaml
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── pom.xml
├── src/main/java/com/delivery/
│   ├── common/
│   │   ├── config/
│   │   ├── exception/
│   │   ├── security/
│   │   ├── dto/
│   │   └── util/
│   ├── auth/{controller,service,dto,entity,repository,mapper}/
│   ├── tenant/{controller,service,dto,entity,repository,mapper}/
│   ├── vendor/{controller,service,dto,entity,repository,mapper}/
│   ├── catalog/{controller,service,dto,entity,repository,mapper}/
│   ├── inventory/{controller,service,dto,entity,repository,mapper}/
│   ├── geo/{controller,service,dto,entity,repository,mapper}/
│   ├── cart/{controller,service,dto,entity,repository,mapper}/
│   ├── order/{controller,service,dto,entity,repository,mapper}/
│   ├── payment/{controller,service,dto,entity,repository,mapper}/
│   ├── dispatch/{controller,service,dto,entity,repository,mapper}/
│   ├── delivery/{controller,service,dto,entity,repository,mapper}/
│   ├── notification/{controller,service,dto,entity,repository,mapper}/
│   ├── support/{controller,service,dto,entity,repository,mapper}/
│   ├── finance/{controller,service,dto,entity,repository,mapper}/
│   └── dashboard/{controller,service,dto}/
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
└── src/test/java/com/delivery/

web/
├── package.json
└── src/
    ├── app/
    ├── components/
    ├── features/{admin,vendor,operations,finance,support}/
    └── services/

mobile/
├── pubspec.yaml
└── lib/
    ├── core/{api,auth,config,errors,widgets}/
    └── features/{auth,customer_home,vendor_discovery,catalog,cart,checkout,orders,courier_jobs,delivery_confirmation,profile}/

docs/
├── architecture.md
├── api.md
└── local-development.md

docker-compose.yml
README.md
```

**Structure Decision**: Use a backend-first monorepo with `backend/`, `web/`,
`mobile/`, and `docs/`. Backend packages are grouped by business feature under
`com.delivery` and use only the simple layers required by the constitution. Web and
mobile use feature folders and direct API service layers. Shared framework concerns stay
in `common` or each client application's small `core` folder.

## Complexity Tracking

No constitution violations are required.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | Not applicable | Not applicable |

## Phase 0: Research Summary

Research decisions are captured in [research.md](./research.md). Key decisions: Spring
Boot layered monolith for MVP, PostgreSQL/PostGIS with Flyway, Keycloak JWT auth,
mock/local payment provider, persisted notifications, status-based tracking, and simple
feature-folder clients.

## Phase 1: Design Summary

Design artifacts are captured in [data-model.md](./data-model.md),
[contracts/openapi.yaml](./contracts/openapi.yaml), and [quickstart.md](./quickstart.md).
The API base path is `/api/v1`. The initial contract covers the MVP surfaces needed for
tenant/user setup, vendor/catalog/inventory, search/cart/checkout/orders, payments,
refunds, couriers/dispatch/deliveries, notifications, support, finance, dashboards, and
admin operations.

## Implementation Phases

1. Repository and local environment: Docker Compose with PostgreSQL/PostGIS, Redis,
   Keycloak, MinIO, and backend application.
2. Backend skeleton and common infrastructure: Maven project, package structure,
   exception handling, validation, OpenAPI, logging, correlation IDs, health, metrics,
   Flyway baseline.
3. Authentication and authorization: Keycloak JWT resource server, role mapping,
   endpoint authorization, tenant context extraction.
4. Tenant and user profile: tenants, app user profiles, roles, `/api/v1/me`, tenant
   isolation helpers in services.
5. Vendor onboarding: vendor profile, documents metadata, opening hours, availability,
   verification workflow.
6. Catalog and inventory: categories, products, SKUs, stock, fuel blocking, pharmacy
   prescription metadata flagging.
7. Geospatial vendor search: PostGIS vendor locations and zones, nearby search, filters
   by category, distance, rating, delivery time, and availability.
8. Cart and pricing: single-vendor cart, cart items, fees, taxes, discounts, delivery
   or pickup selection, validation.
9. Checkout and order creation: order references, order items, initial statuses,
   delivery confirmation code generation/hash, audit log.
10. Payment mock and confirmation: local payment records, idempotent confirmation,
    refund records, partial/total refund support.
11. Vendor fulfillment: accept/reject with reason, preparation, ready for pickup,
    notifications.
12. Dispatch and courier assignment: courier profile, availability, zones, dispatch job
    assignment, accept/reject/reassign.
13. Delivery confirmation: pickup and drop-off lifecycle, proof metadata, cash collected,
    confirmation code validation, delivered/failed states.
14. Notifications and tracking status: persisted notifications, status views, local logs.
15. Support, finance, and dashboards: tickets, audit views, transactions, commissions,
    cash reconciliation, required dashboard summaries.
16. Web portal: role-based Admin, Vendor, Operations, Finance, and Support pages with
    loading, empty, error, and forbidden states.
17. Flutter customer and courier MVP screens: auth, customer discovery/cart/checkout,
    order tracking/code display, courier jobs, delivery confirmation, earnings summary.
18. Tests, documentation, and production hardening: service/unit, repository/integration,
    API tests, security/rate limiting, README, docs, final constitution check.
