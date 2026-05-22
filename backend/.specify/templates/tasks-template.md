---

description: "Task list template for feature implementation"
---

# Tasks: [FEATURE NAME]

**Input**: Design documents from `/specs/[###-feature-name]/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Tests are REQUIRED for production code. Include unit tests for service
business rules, integration tests for repositories and critical flows, and API tests
for checkout, payment confirmation, order lifecycle, delivery confirmation, or other
critical endpoints touched by the feature. The project coverage target is 80%.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Backend**: `backend/src/main/java/com/delivery/[feature]/`, `backend/src/main/resources/db/migration/`, `backend/src/test/`
- **Web backoffice**: `frontend/src/features/[feature]/`
- **Mobile**: `mobile/src/features/[feature]/`, `ios/`, or `android/` per selected platform
- Backend feature packages MUST keep the simple layered structure: controller, service,
  repository, entity, dto, mapper, config, exception, security as needed.

<!-- 
  ============================================================================
  IMPORTANT: The tasks below are SAMPLE TASKS for illustration purposes only.
  
  The /speckit.tasks command MUST replace these with actual tasks based on:
  - User stories from spec.md (with their priorities P1, P2, P3...)
  - Feature requirements from plan.md
  - Entities from data-model.md
  - Endpoints from contracts/
  
  Tasks MUST be organized by user story so each story can be:
  - Implemented independently
  - Tested independently
  - Delivered as an MVP increment
  
  DO NOT keep these sample tasks in the generated tasks.md file.
  ============================================================================
-->

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Create project structure per implementation plan
- [ ] T002 Initialize [language] project with [framework] dependencies
- [ ] T003 [P] Configure linting and formatting tools

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

Examples of foundational tasks (adjust based on your project):

- [ ] T004 Setup database schema and migrations framework
- [ ] T005 [P] Implement authentication/authorization framework
- [ ] T006 [P] Setup API routing and middleware structure
- [ ] T007 Create base models/entities that all stories depend on
- [ ] T008 Configure error handling and logging infrastructure
- [ ] T009 Setup environment configuration management
- [ ] T010 Configure Keycloak/OAuth2 JWT security and role-based access checks
- [ ] T011 Configure structured logging, correlation IDs, health checks, and metrics
- [ ] T012 Configure test coverage reporting with 80% minimum target

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - [Title] (Priority: P1) 🎯 MVP

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 1

> **NOTE: Write tests before implementation when the behavior is new or changed.**

- [ ] T013 [P] [US1] Unit test for [service business rule] in backend/src/test/[path]
- [ ] T014 [P] [US1] Repository or critical-flow integration test in backend/src/test/[path]
- [ ] T015 [P] [US1] API test for [endpoint/user journey] in backend/src/test/[path]

### Implementation for User Story 1

- [ ] T016 [P] [US1] Create entity in backend/src/main/java/com/delivery/[feature]/entity/[Entity].java
- [ ] T017 [P] [US1] Create DTOs in backend/src/main/java/com/delivery/[feature]/dto/
- [ ] T018 [P] [US1] Create mapper in backend/src/main/java/com/delivery/[feature]/mapper/[Mapper].java
- [ ] T019 [US1] Create repository in backend/src/main/java/com/delivery/[feature]/repository/[Repository].java
- [ ] T020 [US1] Implement service in backend/src/main/java/com/delivery/[feature]/service/[Service].java
- [ ] T021 [US1] Implement REST controller in backend/src/main/java/com/delivery/[feature]/controller/[Controller].java
- [ ] T022 [US1] Add Flyway migration, constraints, indexes, and optimistic locking where needed
- [ ] T023 [US1] Add validation, consistent errors, OpenAPI metadata, and useful business-flow logs

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - [Title] (Priority: P2)

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 2

- [ ] T024 [P] [US2] Unit test for [service business rule] in backend/src/test/[path]
- [ ] T025 [P] [US2] Integration/API test for [user journey] in backend/src/test/[path]

### Implementation for User Story 2

- [ ] T026 [P] [US2] Create or update DTO/entity/mapper classes in backend/src/main/java/com/delivery/[feature]/
- [ ] T027 [US2] Implement service method with clear transaction boundary
- [ ] T028 [US2] Implement REST endpoint or UI/mobile screen for [feature]
- [ ] T029 [US2] Add validation, security checks, and observability for user story 2

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - [Title] (Priority: P3)

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 3

- [ ] T030 [P] [US3] Unit test for [service business rule] in backend/src/test/[path]
- [ ] T031 [P] [US3] Integration/API test for [user journey] in backend/src/test/[path]

### Implementation for User Story 3

- [ ] T032 [P] [US3] Create or update simple layered backend or feature-folder UI files
- [ ] T033 [US3] Implement service/screen behavior for [feature]
- [ ] T034 [US3] Add validation, security checks, and observability for user story 3

**Checkpoint**: All user stories should now be independently functional

---

[Add more user story phases as needed, following the same pattern]

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] TXXX [P] Documentation updates in docs/
- [ ] TXXX Code cleanup and refactoring
- [ ] TXXX Remove any placeholder or fake implementation code
- [ ] TXXX Verify public class and public service method comments are present
- [ ] TXXX Verify OpenAPI metadata for changed endpoints
- [ ] TXXX [P] Additional tests needed to maintain 80% minimum coverage
- [ ] TXXX Security hardening
- [ ] TXXX Validate structured logs do not expose secrets, tokens, payment data, prescription files, or sensitive personal data
- [ ] TXXX Run quickstart.md validation

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - May integrate with US1 but should be independently testable
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - May integrate with US1/US2 but should be independently testable

### Within Each User Story

- Tests MUST be written before or alongside implementation for changed behavior
- Entities/DTOs/mappers before services
- Services before endpoints
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2)
- Once Foundational phase completes, all user stories can start in parallel (if team capacity allows)
- All tests for a user story marked [P] can run in parallel
- Models within a story marked [P] can run in parallel
- Different user stories can be worked on in parallel by different team members

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Unit test for [service business rule] in backend/src/test/[path]"
Task: "Integration/API test for [user journey] in backend/src/test/[path]"

# Launch simple layered files for User Story 1 together when they do not conflict:
Task: "Create DTOs in backend/src/main/java/com/delivery/[feature]/dto/"
Task: "Create mapper in backend/src/main/java/com/delivery/[feature]/mapper/[Mapper].java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo
4. Add User Story 3 → Test independently → Deploy/Demo
5. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1
   - Developer B: User Story 2
   - Developer C: User Story 3
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify new behavior is covered by unit, integration, or API tests as required
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence,
  prohibited architecture patterns, and placeholder code that pretends to be complete
