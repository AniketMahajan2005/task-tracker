# Team Task Tracker API

A REST API for managing projects, project members and tasks, built for the Axionix Backend Engineering Internship assignment.

**Stack:** Java 21 · Spring Boot 3.5 · Spring Security + JWT · Spring Data JPA (Hibernate) · PostgreSQL 16 · Flyway · springdoc (Swagger UI) · JUnit 5 + MockMvc

---

## 1. Prerequisites

Pick **one** of the two ways to run it.

| Option | You need |
|---|---|
| A. Docker (easiest) | Docker with Docker Compose v2 |
| B. Native | JDK 21, Maven 3.9+, a PostgreSQL 14+ server (or just run the `db` container from option A) |

The automated tests need only JDK 21 and Maven (they use an in-memory database).

## 2. Environment variables

Copy the template and edit it. The real `.env` is git-ignored and must never be committed.

```bash
cp .env.example .env
```

| Variable | Purpose | Example |
|---|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Used by the Postgres container | `tasktracker` / `tasktracker` / `change-me` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Used by the app when run natively | `jdbc:postgresql://localhost:5432/tasktracker` |
| `JWT_SECRET` | HMAC signing key, **at least 32 characters** (the app refuses to start otherwise) | `openssl rand -base64 48` |
| `JWT_EXPIRATION_MINUTES` | Token lifetime | `60` |
| `SERVER_PORT` | HTTP port | `8080` |

Make sure `POSTGRES_PASSWORD` and `DB_PASSWORD` have the same value, and set `JWT_SECRET` to something long and random.

## 3. Run the application

### Option A: Docker Compose (database + API)

```bash
docker compose up --build
```

### Option B: Native

```bash
# 1. start only the database (or use your own PostgreSQL and point DB_URL at it)
docker compose up -d db

# 2. start the API (reads values from .env automatically)
./mvnw spring-boot:run
```

The API is now on **http://localhost:8080**.

### Database setup / migrations

Nothing to do by hand. [Flyway](https://flywaydb.org/) runs `src/main/resources/db/migration/V1__init_schema.sql` automatically on startup, so a clean database gets the full schema (tables, foreign keys, unique and check constraints, indexes). Hibernate is set to `validate`, so it only checks the schema and never changes it.

## 4. Run the tests

```bash
./mvnw test
```

Tests run the real HTTP stack (security filter → controllers → services → JPA) with the real Flyway migration against an in-memory H2 database in PostgreSQL mode. No database server or `.env` is needed.

Coverage (25 tests across 3 classes):

- **Auth:** register, password never returned, duplicate email (case-insensitive), validation, login success, login failure, missing token, invalid token
- **Projects:** owner/member on create, non-member forbidden, owner-only add member / delete, list scoping, cascade delete
- **Tasks:** defaults, validation, invalid assignee, assign / unassign, `completed_at` lifecycle, PATCH semantics, delete authorization, filtering, pagination limits, sorting, summary, error shape

## 5. API documentation

- **Swagger UI:** http://localhost:8080/swagger-ui.html (click *Authorize* and paste the `access_token`)
- **OpenAPI JSON:** http://localhost:8080/v3/api-docs

### Endpoints

| Method | Endpoint | Purpose | Who |
|---|---|---|---|
| POST | `/auth/register` | Create user | public |
| POST | `/auth/login` | Get a JWT | public |
| POST | `/projects` | Create project (creator becomes owner and member) | any user |
| GET | `/projects` | List projects the user is a member of | any user |
| GET | `/projects/{projectId}` | View project and members | members |
| POST | `/projects/{projectId}/members` | Add a registered user by email | owner |
| DELETE | `/projects/{projectId}` | Delete project and its tasks | owner |
| POST | `/projects/{projectId}/tasks` | Create task | members |
| GET | `/projects/{projectId}/tasks` | Paginated, filtered, sorted list | members |
| GET | `/projects/{projectId}/summary` | Counts by status and priority | members |
| PATCH | `/tasks/{taskId}` | Partial update | members |
| DELETE | `/tasks/{taskId}` | Delete task | project owner or task creator |

JSON field names are `snake_case` (`due_date`, `assignee_id`, `created_at`, `completed_at`, ...).

**List query parameters** for `GET /projects/{id}/tasks`: `status`, `priority`, `assignee_id`, `due_from`, `due_to` (inclusive, ISO-8601), `sort_by` (`created_at` default, or `due_date`), `order` (`asc`, or `desc` default), `page` (1-based, default 1), `size` (default 10, max 50).

### Error format

Every error has the same shape:

```json
{ "error": { "code": "FORBIDDEN", "message": "You are not a member of this project" } }
```

Validation errors add a `details` array: `[{ "field": "password", "message": "..." }]`.

| HTTP | `code` | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | invalid field, bad query parameter, page size > 50, past due date |
| 400 | `MALFORMED_REQUEST` | broken JSON, unknown enum value, badly formatted date |
| 401 | `MISSING_TOKEN` / `INVALID_TOKEN` | no, bad or expired `Authorization` header |
| 401 | `INVALID_CREDENTIALS` | wrong email or password |
| 403 | `FORBIDDEN` | not a member, or not the owner / creator for a restricted action |
| 404 | `NOT_FOUND` | unknown project, task, user email or URL |
| 409 | `DUPLICATE_EMAIL` / `ALREADY_MEMBER` | uniqueness conflicts |
| 422 | `INVALID_ASSIGNEE` | assignee is not a member of the project |
| 500 | `INTERNAL_ERROR` | unexpected failure (details only in server logs) |

### Quick walkthrough with curl

```bash
BASE=http://localhost:8080

# register and log in two users
curl -s -X POST $BASE/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Alice","email":"alice@example.com","password":"Password123"}'
curl -s -X POST $BASE/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Bob","email":"bob@example.com","password":"Password123"}'

TOKEN=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"Password123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')

# create a project, add Bob, create a task assigned to Bob
curl -s -X POST $BASE/projects -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Website","description":"Relaunch"}'
curl -s -X POST $BASE/projects/1/members -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"email":"bob@example.com"}'
curl -s -X POST $BASE/projects/1/tasks -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Design homepage","priority":"HIGH","due_date":"2030-01-31T10:00:00Z","assignee_id":2}'

# update, filter, summary
curl -s -X PATCH $BASE/tasks/1 -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"status":"DONE"}'
curl -s "$BASE/projects/1/tasks?status=DONE&sort_by=due_date&order=asc" -H "Authorization: Bearer $TOKEN"
curl -s $BASE/projects/1/summary -H "Authorization: Bearer $TOKEN"

# a rejected request (no token)
curl -i $BASE/projects
```

## 6. Project structure

```
src/main/java/com/axionix/tasktracker
├── controller/   HTTP only: routing, @Valid, status codes (AuthController, ProjectController, TaskController)
├── service/      business rules (AuthService, ProjectService, TaskService, ProjectAccessService)
├── repository/   Spring Data JPA interfaces + TaskSpecifications (dynamic filters)
├── entity/       JPA entities and enums (User, Project, ProjectMember, Task, Priority, TaskStatus)
├── dto/          request / response records (entities are never returned directly)
├── security/     JWT creation/validation, request filter, 401 handler
├── exception/    ApiException, ErrorCode, GlobalExceptionHandler
└── config/       SecurityConfig, OpenApiConfig, JacksonConfig
src/main/resources/db/migration/   Flyway migrations
src/test/java/                     API tests
```

Request flow: `JwtAuthenticationFilter` → controller → service → repository → database. All "who may do what" decisions live in `ProjectAccessService`.

## 7. Assumptions and design decisions

- **Email** is trimmed and stored lower-cased, so uniqueness is case-insensitive. Login also ignores case.
- **Password:** 8 to 72 characters (72 is BCrypt's limit). Stored as a BCrypt hash, never returned.
- **Token:** JWT (HS256), subject = user id, default lifetime 60 minutes. No refresh tokens.
- **Non-members get `403 FORBIDDEN`**, not 404, so the error clearly says what is wrong. The trade-off is that it confirms a project id exists; hiding that would mean returning 404 instead.
- **Past due dates are rejected** (`400 VALIDATION_ERROR`) whenever a due date is set or changed. An existing overdue task can still be edited because an unchanged due date is not re-validated. `due_date` must be a full ISO-8601 date-time with offset, e.g. `2030-01-31T10:00:00Z`.
- **Task defaults:** new tasks start as `TODO`; `priority` defaults to `MEDIUM`. Status changes happen through `PATCH`.
- **`completed_at`** is stamped the first time a task becomes `DONE`, kept if `DONE` is saved again, and cleared when the task moves to any other status. The rule lives in `Task.changeStatus`.
- **PATCH** leaves fields that are not sent unchanged. Sending `"due_date": null` or `"assignee_id": null` clears them.
- **Assignee** must be a member of the same project, otherwise `422 INVALID_ASSIGNEE`.
- **Pagination:** `page` is 1-based; a `size` above 50 is rejected with 400 rather than silently reduced. Sorting uses `id` as a tie-breaker so pages are stable. Tasks without a due date sort last in ascending and first in descending `due_date` order (PostgreSQL's default null ordering).
- **Deleting a project** removes its members and tasks through database `ON DELETE CASCADE`.
- **Tests use H2** in PostgreSQL mode for zero-setup repeatability; production uses PostgreSQL. The migration uses only portable SQL.

## 8. Known limitations

- No refresh tokens, token revocation or rate limiting.
- No endpoint to remove a member or transfer ownership (the owner is permanent).
- `GET /projects` is not paginated.
- Tests run on H2, not PostgreSQL, so PostgreSQL-specific behaviour is not covered by the test suite.
- No Maven wrapper is committed; install Maven 3.9+ or use Docker.

## 9. AI usage

See [AI_USAGE.md](../task-tracker/AI_USAGE.md).
