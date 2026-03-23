# SplitMate API

Production-ready **Spring Boot 3.3** REST API for expense sharing (Splitwise clone).  
Auth via **Supabase JWT** (email/password + Google SSO). Database: **PostgreSQL** (Supabase or Neon). Deploy target: **Render**.

---

## Table of Contents

1. [Architecture](#architecture)
2. [Tech Stack](#tech-stack)
3. [Prerequisites](#prerequisites)
4. [Environment Variables](#environment-variables)
5. [Supabase Setup](#supabase-setup)
6. [Local Development](#local-development)
7. [API Reference](#api-reference)
8. [Settlement Algorithm](#settlement-algorithm)
9. [Render Deployment](#render-deployment)
10. [Running Tests](#running-tests)

---

## Architecture

```
Client (mobile / web)
       │  Bearer JWT (Supabase)
       ▼
┌──────────────────────────────────────────────┐
│             Spring Boot 3.3 API              │
│                                              │
│  Controllers  →  Services  →  Repositories  │
│      ↓               ↓              ↓        │
│  @Valid DTOs    Business Logic    JPA/Hibernate│
│                                              │
│  Spring Security (OAuth2 Resource Server)    │
│    validates JWT via Supabase JWKS endpoint  │
└──────────────────────────────────────────────┘
                    │
                    ▼
          PostgreSQL (Supabase / Neon)
```

### Entity Model

```
User (supabase_user_id PK, name, email)
  │
  ├──< GroupMember (group_id + user_id PK, role, joined_at)
  │         │
  │    Group (id UUID PK, name, creator_id, invite_token, created_at)
  │
  └──< ExpenseShare (expense_id + user_id PK, share_amount)
            │
       Expense (id UUID PK, group_id, description, amount,
                payer_id, split_type, created_by, created_at)
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.3 |
| Build | Maven |
| Language | Java 17 |
| ORM | Spring Data JPA / Hibernate 6 |
| Database | PostgreSQL (via Supabase or Neon) |
| Auth | Spring Security OAuth2 Resource Server (Supabase JWKS) |
| Docs | SpringDoc OpenAPI 2.5 (Swagger UI) |
| Validation | Jakarta Bean Validation |
| Utilities | Lombok |
| Testing | JUnit 5 + Mockito |
| Deploy | Render (render.yaml included) |

---

## Prerequisites

- Java 17+
- Maven 3.9+
- A **Supabase** project (free tier works)
- A **PostgreSQL** database (Supabase DB or Neon)

---

## Environment Variables

Copy `.env.example` to `.env` and fill in the values.

| Variable | Description | Example |
|---|---|---|
| `DB_URL` | JDBC URL for your PostgreSQL database | `postgresql://postgres:pass@db.xxx.supabase.co:5432/postgres?sslmode=require` |
| `SUPABASE_JWKS_URL` | Supabase JWKS endpoint for JWT validation | `https://xxx.supabase.co/auth/v1/.well-known/jwks.json` |
| `PORT` | HTTP port (Render sets this automatically) | `8080` |

> **Never commit `.env` to version control.**

---

## Supabase Setup

### 1. Create a Supabase Project

1. Go to [supabase.com](https://supabase.com) → **New project**
2. Note your **Project Reference** (e.g. `abcdefghijklmnop`)

### 2. Get the Database URL

```
Project Settings → Database → Connection string → URI
```

Paste as `DB_URL`. Append `?sslmode=require` if not already present.

### 3. Get the JWKS URL

```
Project Settings → API → JWT Settings
```

Your JWKS URL is:
```
https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json
```

### 4. Enable Email Auth

```
Authentication → Providers → Email → Enable
```

### 5. Enable Google SSO (optional)

```
Authentication → Providers → Google → Enable
```

Supply your Google OAuth2 Client ID and Secret from [Google Cloud Console](https://console.cloud.google.com).

### 6. Add Redirect URL

```
Authentication → URL Configuration → Redirect URLs
```

Add your frontend URL (e.g. `http://localhost:3000/**`).

---

## Local Development

```bash
# 1. Clone the repo
git clone https://github.com/your-org/splitmate-api
cd splitmate-api

# 2. Set environment variables (any of the options below)
#    Option A – export in shell
export DB_URL="postgresql://..."
export SUPABASE_JWKS_URL="https://..."

#    Option B – IDE run configuration
#    Option C – create application-local.yml (not committed)

# 3. Build & run
mvn spring-boot:run

# API is now available at http://localhost:8080
# Swagger UI:          http://localhost:8080/swagger-ui.html
# Health check:        http://localhost:8080/actuator/health
```

> Hibernate `ddl-auto: update` will auto-create all tables on first run.

---

## API Reference

All endpoints require `Authorization: Bearer <supabase-access-token>` except the health check and Swagger UI.

### Authentication

Pass the Supabase JWT `access_token` (returned by the Supabase JS/mobile client after sign-in) as a Bearer token.

On first request, SplitMate auto-creates the user record from the JWT claims — no separate registration step needed.

---

### Groups  `POST /api/groups`

#### `GET /api/groups`
List all groups the authenticated user belongs to.

**Response:** `200 OK`
```json
[
  {
    "id": "550e8400-...",
    "name": "Weekend Trip",
    "creatorId": "supabase-user-uuid",
    "memberCount": 4,
    "createdAt": "2025-01-15T10:30:00Z"
  }
]
```

#### `POST /api/groups`
Create a new group. Creator is automatically added as `ADMIN`.

**Body:**
```json
{ "name": "Weekend Trip" }
```

#### `GET /api/groups/{id}`
Get group details.

#### `DELETE /api/groups/{id}`
Delete a group (creator only). Cascades all expenses, shares, and members.

#### `POST /api/groups/{id}/invite`
Generate a shareable invite link.

**Body (optional):**
```json
{ "email": "friend@example.com" }
```

**Response:**
```json
{
  "groupId": "550e8400-...",
  "inviteToken": "a1b2c3d4-...",
  "inviteLink": "https://yourapi.com/api/groups/join/a1b2c3d4-...",
  "email": "friend@example.com"
}
```

#### `POST /api/groups/join/{token}`
Join a group via invite token.

#### `DELETE /api/groups/{id}/leave`
Leave a group (creator cannot leave — delete the group instead).

#### `GET /api/groups/{id}/members`
List all group members with name, email, role, and join date.

#### `GET /api/groups/{id}/expenses?page=0&size=20`
Paginated list of expenses, newest first.

#### `GET /api/groups/{id}/balances`
**Core endpoint.** Returns per-user net balances and the minimum-transfer settlement plan.

**Response:**
```json
{
  "userBalances": [
    { "userId": "alice-id", "userName": "Alice", "netBalance": 20.00 },
    { "userId": "bob-id",   "userName": "Bob",   "netBalance": -10.00 },
    { "userId": "carol-id", "userName": "Carol", "netBalance": -10.00 }
  ],
  "settlements": [
    { "fromUserId": "bob-id",   "fromUserName": "Bob",   "toUserId": "alice-id", "toUserName": "Alice", "amount": 10.00 },
    { "fromUserId": "carol-id", "fromUserName": "Carol", "toUserId": "alice-id", "toUserName": "Alice", "amount": 10.00 }
  ]
}
```

---

### Expenses  `/api/expenses`

#### `POST /api/expenses`
Create an expense.

**Body:**
```json
{
  "groupId": "550e8400-...",
  "description": "Dinner at La Piazza",
  "amount": 120.00,
  "payerId": "alice-supabase-id",
  "splitType": "EQUAL",
  "shares": [
    { "userId": "alice-id", "value": 0 },
    { "userId": "bob-id",   "value": 0 },
    { "userId": "carol-id", "value": 0 }
  ]
}
```

**splitType options:**

| Value | `value` field meaning | Constraint |
|---|---|---|
| `EQUAL` | Ignored — divide evenly | — |
| `EXACT` | Exact dollar amount per person | Must sum to `amount` |
| `PERCENTAGE` | Percentage (0-100) per person | Must sum to 100 |
| `SHARES` | Relative weight per person | Any positive weights |

#### `GET /api/expenses/{id}`
Get a single expense with its shares.

#### `PUT /api/expenses/{id}`
Update an expense (creator or payer only). Re-computes all shares.

#### `DELETE /api/expenses/{id}`
Delete an expense and its shares (creator only).

---

### Error Responses

All errors follow [RFC 7807 Problem Details](https://www.rfc-editor.org/rfc/rfc7807):

```json
{
  "type": "about:blank",
  "title": "Validation Error",
  "status": 400,
  "detail": "Request validation failed",
  "errors": {
    "amount": "amount must be greater than 0",
    "splitType": "splitType is required"
  }
}
```

| HTTP Status | Thrown when |
|---|---|
| `400` | Bean validation failure |
| `401` | Missing or invalid JWT |
| `403` | Not a group member, or insufficient role |
| `404` | Group / expense not found |
| `422` | Business rule violation (e.g. shares don't sum correctly) |

---

## Settlement Algorithm

The debt-simplification algorithm is in `SettlementService.simplifyDebts()`.

### Net Balance Formula

For each user **U** in a group:

```
netBalance[U] = Σ expense.amount  (where expense.payerId == U)
              − Σ share.shareAmount (where share.userId == U)
```

- **Positive** → U is owed money (creditor)
- **Negative** → U owes money (debtor)

### Minimization (Greedy)

```
while creditors and debtors exist:
    creditor ← user with max positive balance
    debtor   ← user with max negative balance
    transfer ← min(|creditor balance|, |debtor balance|)
    record settlement: debtor pays creditor `transfer`
    update both balances
```

This produces the **minimum number of transfers** required to settle all debts.

### Example

| Expense | Payer | Amount | Split |
|---|---|---|---|
| Dinner | Alice | $30 | Equal (3 people) |
| Hotel | Bob | $60 | Equal (3 people) |

Net balances: Alice +$20, Bob +$40, Carol −$60  
→ 2 settlements: Carol → Alice $20, Carol → Bob $40

---

## Render Deployment

### 1. Push to GitHub

```bash
git add .
git commit -m "feat: initial SplitMate API"
git push origin main
```

### 2. Create Render Web Service

1. Go to [render.com](https://render.com) → **New Web Service**
2. Connect your GitHub repository
3. Render auto-detects `render.yaml`

### 3. Set Environment Variables

In Render Dashboard → **Environment**:

| Key | Value |
|---|---|
| `DB_URL` | Your Supabase/Neon JDBC URL |
| `SUPABASE_JWKS_URL` | Your Supabase JWKS URL |

`PORT` is set automatically by Render.

### 4. Deploy

Click **Manual Deploy** or push to `main`. Build command:
```bash
mvn clean package -DskipTests
```

Start command:
```bash
java -jar target/splitmate-api-1.0.0.jar
```

Health check endpoint: `/actuator/health`

### Render Free Tier Note
Free instances spin down after 15 minutes of inactivity. Use a paid plan or an uptime monitor for production.

---

## Running Tests

```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=SettlementServiceTest

# Run with verbose output
mvn test -Dtest=SettlementServiceTest#equalSplitThreePeople
```

Test coverage includes:
- `SettlementServiceTest` — 5 scenarios covering the debt-simplification algorithm
- `ExpenseServiceTest` — all 4 split types (EQUAL, EXACT, PERCENTAGE, SHARES), including error cases

---

## Project Structure

```
splitmate-api/
├── pom.xml
├── render.yaml
├── .env.example
├── README.md
└── src/
    ├── main/
    │   ├── java/com/splitmate/
    │   │   ├── SplitMateApplication.java
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java      # OAuth2 Resource Server
    │   │   │   ├── OpenApiConfig.java       # Swagger / OpenAPI 3
    │   │   │   └── WebConfig.java           # CORS
    │   │   ├── controller/
    │   │   │   ├── GroupController.java     # /api/groups/**
    │   │   │   └── ExpenseController.java   # /api/expenses/**
    │   │   ├── service/
    │   │   │   ├── UserService.java         # JWT → User auto-creation
    │   │   │   ├── GroupService.java
    │   │   │   ├── ExpenseService.java      # Split calculators
    │   │   │   └── SettlementService.java   # Debt simplification
    │   │   ├── repository/                  # Spring Data JPA interfaces
    │   │   ├── entity/                      # JPA entities
    │   │   ├── dto/
    │   │   │   ├── request/                 # @Valid request bodies
    │   │   │   └── response/                # API response shapes
    │   │   ├── enums/
    │   │   │   ├── SplitType.java
    │   │   │   └── MemberRole.java
    │   │   └── exception/
    │   │       ├── GlobalExceptionHandler.java  # RFC 7807 ProblemDetail
    │   │       ├── ResourceNotFoundException.java
    │   │       ├── ForbiddenException.java
    │   │       └── BusinessException.java
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/com/splitmate/service/
            ├── SettlementServiceTest.java
            └── ExpenseServiceTest.java
```
