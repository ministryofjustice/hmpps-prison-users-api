# AGENTS.md

## What this service is
- Kotlin/Spring Boot API for prison user data: users, NOMIS-style user accounts, accessible caseloads, and migration/sync/reconciliation endpoints for NOMIS data load and verification.
- Main flow is `resource/` → `service/` → `jpa/repository/` → PostgreSQL/Flyway. Start with `src/main/kotlin/uk/gov/justice/digital/hmpps/prisonusersapi/resource/` and `service/`.
- Core packages: `resource/` (REST + auth), `service/` (transactions/business rules), `service/converters/` (entity↔DTO mapping), `jpa/` + `jpa/repository/` (entities/repos), `data/` (API DTOs), `config/` (OpenAPI + exception mapping).

## Architecture and data model
- `UserAccount` is the main lookup root for read APIs; it links to `User`, `activeCaseload`, and `userAccessibleCaseloads` (`jpa/UserAccount.kt`).
- Read endpoints currently expose:
  - `GET /users/basic/{username}` in `resource/UserResource.kt`
  - `GET /users/{username}/caseloads` in `resource/UserCaseloadManagementResource.kt`
  - `GET /reconciliation/user/{legacyStaffId}` in `resource/ReconciliationResource.kt` (fully wired to `service/ReconciliationService.kt` and repositories)
- Migration writes are handled in one transaction in `service/MigrationService.kt`: create `User`, validate caseloads, save `UserAccount`s, then save roles and accessible caseload join rows.
- `PUT /sync/user/{legacyStaffId}` in `resource/SyncResource.kt` is currently a controller-level stub (no service/repository wiring yet).
- Response DTOs for reconciliation and sync are in `data/reconciliation/` (e.g. `PrisonUserReconciliationResponse.kt`) and `data/sync/` (e.g. `PrisonUserSyncRequest.kt`), with converters like `User.toPrisonUserReconciliationResponse()` in `service/converters/FromUser.kt`.
- Schema lives in Flyway SQL under `src/main/resources/db/prison-users/`; `V1_0__create_tables.sql` is the quickest way to understand table ownership/cascade rules.

## Local run / build / test
- **Build tooling**: Kotlin 2.4.0, JVM 25; `build.gradle.kts` with `uk.gov.justice.hmpps.gradle-spring-boot` v11.0.0-beta2. hmpps-kotlin-spring-boot-starter is also at beta (3.0.0-beta2).
- Build the jar: `./gradlew clean assemble`
- Run the app + HMPPS Auth in Docker: `docker compose pull && docker compose up`
- Run only auth, then start the app from IntelliJ with profile `dev`: `docker compose pull && docker compose up --scale hmpps-prison-users-api=0`
- For a real local Postgres instead of in-memory H2, start `docker-compose-test.yml` and run with profile `local-postgres` (DB is on `localhost:5434`, credentials are in `src/main/resources/application-local-postgres.yml`).
- Run tests with `./gradlew test`; integration tests use `@SpringBootTest` + `WebTestClient`, not MockMvc.

## Project-specific conventions
- Every API method is expected to carry explicit `@PreAuthorize`; `src/test/kotlin/.../integration/ResourceSecurityTest.kt` fails if an endpoint is missing it.
- OpenAPI annotations are kept directly on controller methods and DTOs (`resource/*.kt`, `data/UserMigrationRequest.kt`). Swagger/OpenAPI is enabled in `dev` and `test`, disabled in base `application.yml`.
- Error responses are centralized in `config/PrisonUsersApiExceptionHandler.kt`; prefer throwing the named service exceptions already used there rather than returning ad hoc `ResponseEntity` errors.
- Mapping logic belongs in `service/converters/`, not controllers. Example: `FromUserAccount.kt` title-cases names and strips DPS caseloads when `removeDpsCaseload = true`. For endpoints returning detailed response objects (e.g., `PrisonUserReconciliationResponse`), converters are extension functions on domain entities (e.g., `User.toPrisonUserReconciliationResponse()` in `FromUser.kt`).
- Reads are explicitly `@Transactional(readOnly = true)` in services (`service/UserService.kt`); writes keep the transaction at service level (`MigrationService.kt`).
- JPA entity graphs control loading and are declared as `@NamedEntityGraph` annotations (`jpa/UserAccount.kt`). Multiple graphs exist for different access patterns:
  - `UserAccount.withCaseloads`: used by `UserAccountRepository.findAllByUserUserId()` for full caseload details
  - `UserAccount.withUserAndActiveCaseload`: used by `UserAccountRepository.findByUsername()` for quick user lookups
  - `UserAccount.caseloads` (default): used by `UserAccountRepository.findById()`
  Preserve or update the graph when adding fields that must be eagerly available to converters.
- Tests build data through `integration/helper/EntityDataLoader.kt` (`DataBuilder`) and authenticate with `JwtAuthorisationHelper` via `IntegrationTestBase.setAuthorisation()`.
- Existing tests prefer nested classes per endpoint/scenario and assert both auth behaviour and payload shape (`resource/UserResourceIntTest.kt`).

## Configuration and integrations
- Auth is HMPPS Auth as an OAuth2 resource server; JWT keys come from `${hmpps-auth.url}/.well-known/jwks.json` (`src/main/resources/application.yml`).
- `dev` uses in-memory H2 with Flyway (`application-dev.yml`); deployed environments use PostgreSQL with datasource values injected from Kubernetes secrets (`helm_deploy/hmpps-prison-users-api/values.yaml`).
- Deployment config is Helm-based under `helm_deploy/`; env-specific overrides (for example dev auth URL and Swagger enablement) are in `values-*.yaml`.
- If you add endpoints, remember there are tests asserting security coverage and OpenAPI availability/validity (`integration/ResourceSecurityTest.kt`, `integration/OpenApiDocsTest.kt`).
