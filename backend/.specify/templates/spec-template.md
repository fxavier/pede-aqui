# Feature Specification: [FEATURE NAME]

**Feature Branch**: `[###-feature-name]`  
**Created**: [DATE]  
**Status**: Draft  
**Input**: User description: "$ARGUMENTS"

## Constitution Alignment *(mandatory)*

- **Affected roles**: [customers, vendors, couriers, administrators, operations users, finance users]
- **Vertical/product scope**: [restaurants, grocery, pharmacy, convenience, electronics, florists, general retail, safe fuel-station convenience products, or N/A]
- **Prohibited scope**: Fuel transport MUST NOT be included.
- **Security and tenant isolation**: [roles, tenant data boundaries, sensitive data handled, rate limiting needs]
- **Reliability rules**: [order state, payment idempotency, refunds, 6-digit delivery code, cash reconciliation, inventory concurrency, or N/A]
- **Simplicity constraint**: Describe the direct layered implementation. Do not specify DDD, Clean Architecture, Hexagonal Architecture, ports, adapters, use cases, command buses, CQRS, event sourcing, or speculative abstractions.

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.
  
  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - [Brief Title] (Priority: P1)

[Describe this user journey in plain language]

**Why this priority**: [Explain the value and why it has this priority level]

**Independent Test**: [Describe how this can be tested independently - e.g., "Can be fully tested by [specific action] and delivers [specific value]"]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]
2. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

### User Story 2 - [Brief Title] (Priority: P2)

[Describe this user journey in plain language]

**Why this priority**: [Explain the value and why it has this priority level]

**Independent Test**: [Describe how this can be tested independently]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

### User Story 3 - [Brief Title] (Priority: P3)

[Describe this user journey in plain language]

**Why this priority**: [Explain the value and why it has this priority level]

**Independent Test**: [Describe how this can be tested independently]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

[Add more user stories as needed, each with an assigned priority]

### Edge Cases

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right edge cases.
-->

- What happens when [boundary condition]?
- How does system handle [error scenario]?
- How does the feature prevent prohibited fuel transport while allowing safe convenience products from fuel stations, if relevant?
- How does the feature preserve tenant isolation when data belongs to a vendor, courier, customer, or operational tenant?
- How does the feature behave on retries, duplicate callbacks, concurrent updates, or stale optimistic-lock versions?

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: System MUST [specific capability, e.g., "allow users to create accounts"]
- **FR-002**: System MUST [specific capability, e.g., "validate email addresses"]  
- **FR-003**: Users MUST be able to [key interaction, e.g., "reset their password"]
- **FR-004**: System MUST [data requirement, e.g., "persist user preferences"]
- **FR-005**: System MUST [behavior, e.g., "log all security events"]
- **FR-006**: System MUST enforce role-based access for [roles allowed to use this feature]
- **FR-007**: System MUST enforce tenant isolation for [tenant-owned data or N/A]
- **FR-008**: System MUST validate all request inputs and return consistent errors
- **FR-009**: System MUST document API endpoints with OpenAPI metadata when APIs are changed

*Example of marking unclear requirements:*

- **FR-010**: System MUST authenticate users via Keycloak OAuth2/OIDC JWT unless [NEEDS CLARIFICATION: feature is not authenticated]
- **FR-011**: System MUST retain user data for [NEEDS CLARIFICATION: retention period not specified]

### Non-Functional Requirements

- **NFR-001**: Implementation MUST follow simple controller/service/repository/entity/dto/mapper layering for backend work.
- **NFR-002**: Implementation MUST use structured logs and correlation IDs for important business flows.
- **NFR-003**: Sensitive data, including secrets, tokens, payment data, prescription files, and sensitive personal data, MUST NOT be logged.
- **NFR-004**: Sensitive endpoints such as authentication, checkout, and payment confirmation MUST have rate limiting.
- **NFR-005**: Tests MUST cover service business rules, critical persistence behavior, and API flows relevant to this feature.

### Key Entities *(include if feature involves data)*

- **[Entity 1]**: [What it represents, key attributes without implementation]
- **[Entity 2]**: [What it represents, relationships to other entities]

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

- **SC-001**: [Measurable metric, e.g., "Users can complete account creation in under 2 minutes"]
- **SC-002**: [Measurable metric, e.g., "System handles 1000 concurrent users without degradation"]
- **SC-003**: [User satisfaction metric, e.g., "90% of users successfully complete primary task on first attempt"]
- **SC-004**: [Business metric, e.g., "Reduce support tickets related to [X] by 50%"]

## Assumptions

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right assumptions based on reasonable defaults
  chosen when the feature description did not specify certain details.
-->

- [Assumption about target users, e.g., "Users have stable internet connectivity"]
- [Assumption about scope boundaries, e.g., "Mobile support is out of scope for v1"]
- [Assumption about data/environment, e.g., "Existing authentication system will be reused"]
- [Dependency on existing system/service, e.g., "Requires access to the existing user profile API"]
