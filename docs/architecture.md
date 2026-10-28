# Architecture

The backend is a layered modular monolith (see `docs/adr/ADR-0002-module-structure.md`). Each top-level package under `com.delivery` is an application module that keeps the layers below internally; modules talk to each other through their public API or transactional application events, never through another module's repositories or entities, and without cycles. Spring Modulith is used only for structural verification and transactional events (adoption in code is a later increment). It deliberately avoids Domain-Driven Design tactical patterns, Clean Architecture, Hexagonal Architecture, ports/adapters, use cases, command buses, CQRS, event sourcing, Kafka/event externalization, and speculative interfaces.

## Backend Layers

- `controller`: REST endpoints, request validation, response DTOs.
- `service`: business logic, transactions, tenant and role checks, state transitions.
- `repository`: Spring Data JPA database access.
- `entity`: JPA table mappings.
- `dto`: request and response payloads.
- `mapper`: entity/DTO conversion.
- `config`: framework configuration.
- `exception`: consistent error handling.
- `security`: authentication and authorization support.

## Tenant Model

Tenant-scoped records include `tenant_id`. Services validate tenant context before returning or changing tenant-owned data. Repositories remain simple Spring Data JPA interfaces and do not hide tenant rules behind abstraction layers.

## Admin and Operations Controls

Admin and operations capabilities are implemented with role-guarded controllers and explicit service methods:

- Admin configuration: zones and fee policies are tenant scoped.
- Operations intervention: dispatch reassignment uses status checks and zone-based courier eligibility.
- Audit trail: sensitive actions are persisted in `audit_logs` with actor, target, action, result, and timestamp.

## Comments

Every public class has a short responsibility comment. Public service methods include short purpose comments. Business rules receive concise comments only when the rule is not obvious.
