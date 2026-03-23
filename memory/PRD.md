# SplitMate API — PRD

## Problem Statement
Production Spring Boot 3.3 REST API for "SplitMate" expense sharing (Splitwise clone).
Auth: Supabase JWT validation (email/password + Google SSO).
Stack: Maven + Lombok + Spring Data JPA/Hibernate + Postgres JDBC (Supabase/Neon).
Deploy target: Render.

## Architecture
- Spring Boot 3.3 + Java 17
- Spring Security OAuth2 Resource Server (Supabase JWKS)
- Spring Data JPA / Hibernate 6 + PostgreSQL
- SpringDoc OpenAPI 2.5 (Swagger UI at /swagger-ui.html)
- JUnit 5 + Mockito tests
- Project root: /app/splitmate-api/

## Entities
- User (supabase_user_id PK, name, email)
- Group → expense_groups table (id UUID, name, creator_id, invite_token, created_at)
- GroupMember (group_id + user_id composite PK, role ADMIN|MEMBER, joined_at)
- Expense (id UUID, group_id, description, amount, payer_id, split_type, created_by, created_at)
- ExpenseShare (expense_id + user_id composite PK, share_amount)

## API Endpoints Implemented
### Groups (/api/groups)
- GET /api/groups — user's groups
- POST /api/groups — create (creator auto-added as ADMIN)
- GET /api/groups/{id} — group details
- DELETE /api/groups/{id} — delete (creator only, cascades)
- POST /api/groups/{id}/invite — generate shareable invite link
- POST /api/groups/join/{token} — join via invite token
- DELETE /api/groups/{id}/leave — leave group
- GET /api/groups/{id}/members — list members
- GET /api/groups/{id}/expenses — paginated expense list
- GET /api/groups/{id}/balances — simplified balances + settlements

### Expenses (/api/expenses)
- POST /api/expenses — create with split calculation
- GET /api/expenses/{id} — get expense
- PUT /api/expenses/{id} — update (creator/payer only)
- DELETE /api/expenses/{id} — delete (creator only)

## Split Types Supported
- EQUAL — divide evenly, rounding remainder to first participant
- EXACT — caller provides exact amount per person (must sum to total)
- PERCENTAGE — caller provides % per person (must sum to 100)
- SHARES — caller provides relative weights (any positive values)

## Settlement Algorithm
Greedy debt-simplification (SettlementService.java):
- netBalance[U] = Σ paid − Σ owed
- Iteratively pair max creditor with max debtor
- Produces minimum number of transfers

## Security
- All /api/** routes require valid Supabase JWT (Bearer token)
- Public: /swagger-ui/**, /v3/api-docs/**, /actuator/health, /api/groups/join/**
- User auto-created on first valid JWT request
- UserId checks on all mutations

## What's Implemented (2026-02)
- [x] Full Maven project structure (45 files)
- [x] All 5 JPA entities with composite PKs
- [x] All 5 Spring Data repositories
- [x] Full CRUD for Groups and Expenses
- [x] All 4 split type calculators
- [x] Debt-simplification settlement algorithm
- [x] Supabase JWT validation via JWKS
- [x] User auto-creation from JWT claims (email/password + Google SSO)
- [x] RFC 7807 ProblemDetail error responses
- [x] OpenAPI / Swagger UI
- [x] JUnit 5 tests: SettlementServiceTest (5 cases) + ExpenseServiceTest (6 cases)
- [x] render.yaml for one-click Render deployment
- [x] Comprehensive README with Supabase + Render setup guide

## Backlog / Future Enhancements
- P1: Multiple payers per expense (ExpensePayer junction table)
- P1: Push notifications when added to group or expense created
- P1: Expense categories / tags
- P2: Settlement recording (mark debt as paid)
- P2: Currency support (multi-currency groups)
- P2: Flyway database migrations (replace ddl-auto: update)
- P2: Integration tests with Testcontainers + PostgreSQL
- P3: Admin panel for group management
- P3: Export expenses to CSV/PDF
