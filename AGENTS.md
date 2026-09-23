# AGENTS.md

## What this service is
- Kotlin/Spring Boot API for prison user data: users, NOMIS-style user accounts, accessible caseloads, and migration/sync/reconciliation endpoints for NOMIS data load and verification.
- Main flow is `resource/` → `service/` → `jpa/repository/` → PostgreSQL/Flyway. Start with `src/main/kotlin/uk/gov/justice/digital/hmpps/prisonusersapi/resource/` and `service/`.
- Core packages: `resource/` (REST + auth), `service/` (transactions/business rules), `service/converters/` (entity↔DTO mapping), `jpa/` + `jpa/repository/` (entities/repos), `data/` (API DTOs), `config/` (OpenAPI + exception mapping).

## Architecture and data model
- `UserAccount` is the main lookup root for read APIs; it links to `User`, `activeCaseload`, and `userAccessibleCaseloads` (`jpa/UserAccount.kt`).
- `UserCaseloadAdministrator` and `UserCaseloadMember` are separate composite-key JPA entities for caseload admin/member rows; both are `@Audited`, so keep their fields aligned with the base tables in `V1_0__create_tables.sql` and the audit tables in `V1_4__add_envers_audit_tables.sql` when changing either side.
- Read endpoints currently expose:
  - `GET /users/basic/{username}` in `resource/UserResource.kt`
  - `POST /users/basic/find-by-usernames` in `resource/UserResource.kt`
  - `GET /users/{username}` in `resource/UserResource.kt`
  - `GET /users/{username}/caseloads` in `resource/UserCaseloadManagementResource.kt`
  - `GET /reference-data/caseloads` in `resource/ReferenceDataResource.kt`
  - `GET /reconciliation/user/{legacyStaffId}` in `resource/ReconciliationResource.kt` (fully wired to `service/ReconciliationService.kt` and repositories)
- `PUT /sync/user/{legacyStaffId}` in `resource/SyncResource.kt` is fully wired to `service/SyncService.kt` (transactional upsert + replace semantics for emails/accounts/roles/caseloads, with DB-backed per-user sync locking; see `SyncLock` entity for lock mechanism).
- Response DTOs for reconciliation and sync are in `data/reconciliation/` (e.g. `PrisonUserReconciliationResponse.kt`) and `data/sync/` (e.g. `PrisonUserSyncRequest.kt`), with converters like `User.toPrisonUserReconciliationResponse()` in `service/converters/FromUser.kt`.
- Schema lives in Flyway SQL under `src/main/resources/db/prison-users/` (base migrations) and `src/main/resources/db/prison-users_postgresql/` (PostgreSQL-specific migrations, e.g. partial indexes).

## Local run / build / test
- **Build tooling**: Kotlin 2.4.10, JVM 25; `build.gradle.kts` with `uk.gov.justice.hmpps.gradle-spring-boot` v11.0.8. `hmpps-kotlin-spring-boot-starter` is 3.0.1, with `hmpps-kotlin-spring-boot-starter-test` at 3.0.1. OpenAPI is `springdoc-openapi-starter-webmvc-ui` 3.1.1; test fixtures use `wiremock-standalone` 3.13.2 and `swagger-parser` 2.1.46.
- Build the jar: `./gradlew clean assemble`
- Build the Docker image locally by assembling first, copying `build/libs/*.jar` into the repository root, then running `docker build --build-arg GIT_REF=... --build-arg GIT_BRANCH=... --build-arg BUILD_NUMBER=... .`; the container expects `HMPPS_AUTH_URL` when started.
- Run the app + HMPPS Auth in Docker: `docker compose pull && docker compose up`
- Run only auth, then start the app from IntelliJ with profile `dev`: `docker compose pull && docker compose up --scale hmpps-prison-users-api=0`
- For a real local Postgres instead of in-memory H2, start `docker-compose-test.yml` and run with profile `local-postgres` (DB is on `localhost:5434`, credentials are in `src/main/resources/application-local-postgres.yml`).
- Run tests with `./gradlew test`; integration tests use `@SpringBootTest` + `WebTestClient`, not MockMvc.

## Project-specific conventions
- Every API method is expected to carry explicit `@PreAuthorize`; `src/test/kotlin/.../integration/ResourceSecurityTest.kt` fails if an endpoint is missing it (except allowlisted endpoints such as `GET /reference-data/caseloads` which is public and returns non-sensitive reference data, and Swagger/error paths).
- OpenAPI annotations are kept directly on controller methods and DTOs (`resource/*.kt`, `data/UserMigrationRequest.kt`). Swagger/OpenAPI is enabled in `dev` and `test`, disabled in base `application.yml`.
- Error responses are centralized in `config/PrisonUsersApiExceptionHandler.kt`; prefer throwing the named service exceptions already used there (e.g., `UserNotFoundException`, `CaseloadNotFoundException`, `ActiveCaseloadNotInUserAccessibleCaseloadsException`, `SyncLockAcquisitionTimeoutException`) rather than returning ad hoc `ResponseEntity` errors. These map to specific HTTP status codes: 404 for not found, 409 for conflicts/lock timeouts, 400 for validation/state violations.
- Mapping logic belongs in `service/converters/`, not controllers. Example: `FromUserAccount.kt` title-cases names and strips DPS caseloads when `removeDpsCaseload = true`. For endpoints returning detailed response objects (e.g., `PrisonUserReconciliationResponse`), converters are extension functions on domain entities (e.g., `User.toPrisonUserReconciliationResponse()` in `FromUser.kt`). Email selection during sync is delegated to `PrimaryEmailDetector.getPrimaryEmail()`, which prioritizes `@justice.gov.uk` addresses.
- Prison/caseload name formatting goes through `service/converters/PrisonNameFormat.kt` (`capitalizeLeavingAbbreviations()`), which keeps abbreviations such as `HMP`, `YOI`, and `VCC` uppercase when title-casing names.
- `AccountStatusConverter` in `jpa/` is `@Converter(autoApply = true)` and persists `AccountStatus.desc`; keep enum descriptions aligned with the stored database values.
- Reads are explicitly `@Transactional(readOnly = true)` in services (`service/UserService.kt`, `service/ReconciliationService.kt`); writes keep the transaction at service level using `TransactionTemplate` for fine-grained control (e.g., `SyncService.kt`).
- JPA entity graphs control loading and are declared as `@NamedEntityGraph` annotations (`jpa/UserAccount.kt`). Multiple graphs exist for different access patterns:
  - `UserAccount.withCaseloads`: used by `UserAccountRepository.findAllByUserUserId()` for full caseload details
  - `UserAccount.withUserAndActiveCaseload`: used by `UserAccountRepository.findByUsername()` for quick user lookups
  - `UserAccount.withUserActiveCaseloadUserRoleCodes`: used by `UserAccountRepository.findWithUserAndActiveCaseloadAndUserRoleCodesByUsername()` for user details with role codes
  - `UserAccount.caseloads` (default): used by `UserAccountRepository.findById()`
  Preserve or update the graph when adding fields that must be eagerly available to converters.
- Tests build data through `integration/helper/EntityDataLoader.kt` (`DataBuilder`) and authenticate with `JwtAuthorisationHelper` via `IntegrationTestBase.setAuthorisation()`.
- Existing tests prefer nested classes per endpoint/scenario and assert both auth behaviour and payload shape (`resource/UserResourceIntTest.kt`).

## Configuration and integrations
- Auth is HMPPS Auth as an OAuth2 resource server; JWT keys come from `${hmpps-auth.url}/.well-known/jwks.json` (`src/main/resources/application.yml`).
- `dev` uses in-memory H2 with Flyway (`application-dev.yml`); deployed environments use PostgreSQL with datasource values injected from Kubernetes secrets (`helm_deploy/hmpps-prison-users-api/values.yaml`).
- `application.yml` enables graceful shutdown and health probes; Spring Boot management endpoints are exposed at `/`, with `/health` and `/info` available for readiness/liveness and diagnostics.
- Sync lock timing is configurable via `sync.lock.retry-backoff-ms` and `sync.lock.max-wait-ms` in `application.yml` and is used by `service/SyncService.kt`.
- `springdoc-openapi-starter-webmvc-ui` 3.1.1 is used for API documentation; when updating, check compatibility with test utilities like `swagger-parser` 2.1.46.
- Deployment config is Helm-based under `helm_deploy/`; env-specific overrides (for example dev auth URL and Swagger enablement) are in `values-*.yaml`.
- For broader HMPPS Kotlin conventions, see the "Common Kotlin patterns" section in `README.md` and the linked tech docs; the project is community managed via `#kotlin-dev`.
- If you add endpoints, remember there are tests asserting security coverage and OpenAPI availability/validity (`integration/ResourceSecurityTest.kt`, `integration/OpenApiDocsTest.kt`).
