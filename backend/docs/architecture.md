# Architecture

The MVP uses a simple layered architecture. It deliberately avoids Domain-Driven Design, Clean Architecture, Hexagonal Architecture, ports/adapters, use cases, command buses, CQRS, event sourcing, Kafka, and speculative interfaces.

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
