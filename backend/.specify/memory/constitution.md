<!--
Sync Impact Report
Version change: template/unset -> 1.0.0
Modified principles:
- Template Principle 1 -> I. Simplicity First
- Template Principle 2 -> II. Simple Layered Backend
- Template Principle 3 -> III. Documentation and Comments
- Template Principle 4 -> IV. Security and Tenant Isolation
- Template Principle 5 -> V. Database and Consistency
- Added VI. Order, Payment, and Delivery Reliability
- Added VII. Observability Without Noise
- Added VIII. Required Testing Discipline
- Added IX. Frontend and Mobile Simplicity
- Added X. Vertical Slice Delivery
Added sections:
- Product and Technical Constraints
- Delivery Workflow and Quality Gates
Removed sections:
- None; template placeholder sections were replaced with concrete sections.
Templates requiring updates:
- updated: .specify/templates/plan-template.md
- updated: .specify/templates/spec-template.md
- updated: .specify/templates/tasks-template.md
- not present: .specify/templates/commands/*.md
Follow-up TODOs:
- None
-->
# Pede Aqui Constitution

## Core Principles

### I. Simplicity First

The project MUST implement the simplest production-safe solution that satisfies the
current requirement. Code MUST be readable, direct, and maintainable. The project MUST
NOT use Domain-Driven Design, Clean Architecture, Hexagonal Architecture, ports,
adapters, use cases, command buses, CQRS, event sourcing, or speculative abstraction
layers. Interfaces, factories, managers, handlers, processors, and orchestration layers
MUST NOT be introduced when a direct service method is sufficient. Premature
optimization is prohibited unless a measured production or test result proves the need.

Rationale: the system must remain easy to understand and change while it grows across
delivery marketplace verticals.

### II. Simple Layered Backend

The backend MUST use a simple layered Spring structure organized by business feature.
Each feature package MUST use the same practical layers when needed: controller,
service, repository, entity, dto, mapper, config, exception, and security. Controllers
MUST expose REST APIs and validate request inputs. DTOs MUST be used for request and
response payloads. Services MUST contain business logic and clear transaction
boundaries. Repositories MUST access PostgreSQL through Spring Data JPA. Entities MUST
represent database tables. Mappers MUST convert between entities and DTOs. Config,
exception, and security classes MUST remain framework and cross-cutting code, not
business orchestration layers.

Rationale: feature packages keep related code close while a consistent layered shape
prevents architectural drift.

### III. Documentation and Comments

Every public class MUST include a short comment explaining its responsibility. Every
public service method MUST include a short comment explaining what it does. Complex
business rules MUST include concise comments explaining the rule and its reason. Obvious
comments that merely repeat code MUST NOT be added. API endpoints MUST be documented
with OpenAPI annotations or generated OpenAPI metadata. README files MUST explain how
to run, test, and configure the project.

Rationale: documentation must help future maintainers understand business intent
without hiding simple code behind noise.

### IV. Security and Tenant Isolation

Authentication and authorization MUST use Keycloak with OAuth2/OIDC and JWT. Access
control MUST be role-based for customers, vendors, couriers, administrators, operations
users, and finance users. Tenant isolation MUST be enforced anywhere tenant data exists.
The application database MUST NOT store user passwords. Logs MUST NOT include secrets,
tokens, payment data, prescription files, or sensitive personal data. All inputs MUST be
validated. Sensitive endpoints, including authentication, checkout, and payment
confirmation, MUST have rate limiting.

Rationale: marketplace data crosses users, vendors, couriers, and finance workflows;
security shortcuts can create direct customer and business harm.

### V. Database and Consistency

PostgreSQL MUST be the primary relational database. Flyway MUST manage database
migrations. Primary keys MUST use UUIDs. Records with concurrent update risk, including
orders, payments, inventory, and dispatch jobs, MUST use optimistic locking. Database
schemas MUST include clear constraints and indexes for frequent queries. Database design
MUST remain understandable and documented through migrations, names, and feature docs.

Rationale: consistency rules must be explicit because order, payment, inventory, and
dispatch data are business-critical.

### VI. Order, Payment, and Delivery Reliability

Order state transitions MUST be validated by service logic. Payment confirmation MUST
be idempotent. Refund operations MUST be auditable. Delivery confirmation MUST require
the customer-provided 6-digit code. Cash-on-delivery reconciliation MUST be tracked
where cash-on-delivery exists. The MVP MUST avoid distributed complexity unless a
single-process or single-database approach cannot meet a documented requirement.

Rationale: delivery marketplaces fail visibly when order, payment, and courier flows are
not reliable and auditable.

### VII. Observability Without Noise

The system MUST use structured logs, correlation IDs, health checks, and metrics.
Important business flows, including checkout, payment confirmation, order lifecycle,
dispatch, delivery confirmation, refund, and cash reconciliation, MUST include useful
logs. Simple methods MUST NOT be over-instrumented when they add no operational value.

Rationale: production support requires traceability, but excessive instrumentation makes
simple flows harder to maintain.

### VIII. Required Testing Discipline

Services containing business rules MUST have unit tests. Repositories and critical
flows MUST have integration tests. Checkout, payment confirmation, order lifecycle, and
delivery confirmation MUST have API tests. The minimum coverage target is 80%. Tests
MUST be easy to understand, maintain, and run in the documented project workflow.

Rationale: the platform handles money, fulfillment, and regulated product constraints;
tests must protect behavior without becoming a second architecture.

### IX. Frontend and Mobile Simplicity

The web backoffice MUST be simple, consistent, and usable. Mobile apps MUST use a
pragmatic feature-folder structure, not Clean Architecture. Frontend and mobile features
MUST prefer screens, services, models, and simple state management. UI state management
MUST remain direct and understandable. Unnecessary frontend and mobile abstractions are
prohibited.

Rationale: customer, vendor, courier, admin, operations, and finance experiences must be
maintainable by feature teams without architectural ceremony.

### X. Vertical Slice Delivery

Implementation MUST proceed one vertical slice at a time. Each task MUST produce
working, testable code. Placeholder code that pretends to be complete is prohibited.
Future features MUST NOT be implemented before the MVP works end-to-end. Clear code is
preferred over clever code in every feature.

Rationale: the platform must reach production value through complete, demonstrable
increments rather than incomplete scaffolding.

## Product and Technical Constraints

The platform serves customers, vendors, couriers, administrators, operations users, and
finance users. It supports restaurants, grocery stores, pharmacies, convenience stores,
electronics shops, florists, general retail, and safe convenience products from fuel
stations. Fuel itself MUST NOT be transported or represented as a deliverable product.

Backend package organization MUST follow feature packages under `com.delivery` with a
consistent simple structure. Typical feature packages include `auth`, `vendor`,
`catalog`, `order`, `payment`, `delivery`, `dispatch`, and `support`. Shared framework
code belongs in `common.config`, `common.exception`, `common.security`, or `common.util`.

The backend stack MUST use Spring Data JPA for repositories, PostgreSQL for persistence,
Flyway for migrations, Keycloak for identity, OAuth2/OIDC JWTs for API authentication,
and OpenAPI metadata for API documentation unless a constitution amendment changes the
stack. New technology choices MUST be justified in the implementation plan and MUST NOT
introduce architectural patterns prohibited by this constitution.

## Delivery Workflow and Quality Gates

Every feature specification MUST identify the user roles affected, the supported
verticals or products in scope, security and tenant-isolation requirements, and testable
success criteria. Any feature involving orders, payments, refunds, dispatch, delivery,
inventory, prescriptions, cash, or personal data MUST explicitly document its reliability
and security rules.

Every implementation plan MUST pass a Constitution Check before design work starts and
again before task generation. The check MUST confirm simplicity, layered package shape,
security, data consistency, reliability, observability, testing, and vertical-slice
delivery. Any complexity exception MUST be documented with the rejected simpler
alternative and the concrete reason the exception is required now.

Every task list MUST produce working code by independently testable user story. Tasks
MUST include documentation, validation, error handling, security, observability, database
migration, and tests when the feature touches those areas. Generated tasks MUST NOT keep
sample placeholders. A story is not complete until its acceptance scenarios and required
tests pass.

## Governance

This constitution supersedes conflicting project conventions, templates, and ad hoc
engineering preferences. Pull requests, implementation plans, and generated tasks MUST
be reviewed for compliance with these principles. Any proposed exception MUST identify
the violated principle, explain why the simpler compliant option is insufficient, and
include a migration or removal plan when the exception is temporary.

Amendments MUST update this file, include a Sync Impact Report, and propagate required
changes to templates and runtime guidance files. Versioning follows semantic versioning:
MAJOR for incompatible governance or principle redefinitions, MINOR for added or
materially expanded principles or sections, and PATCH for clarifications or wording
changes that do not change obligations. The ratification date remains the original
adoption date. The last amended date changes whenever the constitution content changes.

Compliance reviews MUST occur during specification, planning, task generation, and code
review. Incomplete fake implementations, undocumented public APIs, insecure shortcuts,
and prohibited architecture patterns MUST block completion until corrected.

**Version**: 1.0.0 | **Ratified**: 2026-05-10 | **Last Amended**: 2026-05-10
