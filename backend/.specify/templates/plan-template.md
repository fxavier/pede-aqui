# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]
**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: [Java/Spring Boot version, frontend/mobile versions or NEEDS CLARIFICATION]  
**Primary Dependencies**: [Spring Data JPA, Keycloak/OAuth2 resource server, Flyway, OpenAPI, frontend/mobile dependencies or NEEDS CLARIFICATION]  
**Storage**: PostgreSQL with Flyway migrations unless N/A for this slice  
**Testing**: [unit, integration, API test framework and coverage tooling or NEEDS CLARIFICATION]  
**Target Platform**: [backend service, web backoffice, mobile app, or NEEDS CLARIFICATION]
**Project Type**: [backend feature, web feature, mobile feature, or mixed vertical slice]  
**Performance Goals**: [domain-specific, e.g., 1000 req/s, 10k lines/sec, 60 fps or NEEDS CLARIFICATION]  
**Constraints**: [domain-specific, e.g., <200ms p95, <100MB memory, offline-capable or NEEDS CLARIFICATION]  
**Scale/Scope**: [domain-specific, e.g., 10k users, 1M LOC, 50 screens or NEEDS CLARIFICATION]

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

The plan MUST pass all gates before Phase 0 research and again after Phase 1 design.

- **Simplicity First**: Uses direct, readable code. Does not introduce DDD, Clean
  Architecture, Hexagonal Architecture, ports, adapters, use cases, command buses,
  CQRS, event sourcing, or speculative abstractions.
- **Layered Backend**: Backend work is organized by business feature package and uses
  only the needed layers: controller, service, repository, entity, dto, mapper, config,
  exception, security.
- **Security**: Uses Keycloak/OAuth2/OIDC JWTs, role-based access control, tenant
  isolation where tenant data exists, input validation, sensitive endpoint rate
  limiting, and no logging of secrets or sensitive personal/payment data.
- **Database Consistency**: Uses PostgreSQL, Flyway, UUID primary keys, clear
  constraints, indexes for frequent queries, and optimistic locking for concurrent
  records such as orders, payments, inventory, and dispatch jobs.
- **Reliability**: Validates order state transitions, keeps payment confirmation
  idempotent, audits refunds, requires the 6-digit delivery confirmation code, and
  tracks cash-on-delivery reconciliation where applicable.
- **Observability**: Adds structured logs, correlation IDs, health checks, metrics, and
  useful business-flow logs without over-instrumenting simple methods.
- **Testing**: Includes unit tests for service business rules, integration tests for
  repositories and critical flows, API tests for checkout/payment/order/delivery flows,
  and an 80% minimum coverage target.
- **Vertical Slice Delivery**: Delivers one working, independently testable slice. Does
  not include placeholder code or future features before the MVP path works end-to-end.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
backend/
├── src/main/java/com/delivery/
│   ├── auth/{controller,service,dto,entity,repository}/
│   ├── vendor/{controller,service,dto,entity,repository}/
│   ├── catalog/{controller,service,dto,entity,repository}/
│   ├── order/{controller,service,dto,entity,repository}/
│   ├── payment/{controller,service,dto,entity,repository}/
│   ├── delivery/{controller,service,dto,entity,repository}/
│   ├── dispatch/{controller,service,dto,entity,repository}/
│   ├── support/{controller,service,dto,entity,repository}/
│   └── common/{config,exception,security,util}/
├── src/main/resources/db/migration/
└── src/test/

frontend-or-mobile/
└── src/features/[feature]/{screens,services,models,state}/
```

**Structure Decision**: [Document the selected structure and reference the real
directories captured above]

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., extra orchestration layer] | [current need] | [why a direct service method is insufficient] |
| [e.g., additional service dependency] | [specific production requirement] | [why the existing PostgreSQL/Spring approach is insufficient] |
