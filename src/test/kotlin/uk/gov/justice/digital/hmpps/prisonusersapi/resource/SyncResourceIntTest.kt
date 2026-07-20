package uk.gov.justice.digital.hmpps.prisonusersapi.resource

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonusersapi.data.AccountStatus
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UsageType
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserStatus
import uk.gov.justice.digital.hmpps.prisonusersapi.data.migrate.MigratedUser
import uk.gov.justice.digital.hmpps.prisonusersapi.data.migrate.MigratedUserAccessibleCaseload
import uk.gov.justice.digital.hmpps.prisonusersapi.data.migrate.MigratedUserAccount
import uk.gov.justice.digital.hmpps.prisonusersapi.data.migrate.MigratedUserEmail
import uk.gov.justice.digital.hmpps.prisonusersapi.data.migrate.MigratedUserRole
import uk.gov.justice.digital.hmpps.prisonusersapi.data.migrate.UserMigrationRequest
import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.PrisonUserSyncRequest
import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.SyncPrisonUserAccount
import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.SyncPrisonUserCaseload
import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.SyncPrisonUserEmail
import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.SyncPrisonUserRole
import uk.gov.justice.digital.hmpps.prisonusersapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonusersapi.integration.helper.DataBuilder
import uk.gov.justice.digital.hmpps.prisonusersapi.service.MigrationService
import java.time.LocalDateTime

private const val SYNC_ROLE = "ROLE_PRISON_USERS_API__SYNC__RW"
private const val RECONCILIATION_ROLE = "ROLE_PRISON_USERS_API__MIGRATION__RW"

class SyncResourceIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var migrationService: MigrationService

  @Autowired
  private lateinit var dataBuilder: DataBuilder

  @DisplayName("PUT /sync/user/{legacyStaffId}")
  @Nested
  inner class PutPrisonUserForSync {

    private val legacyStaffId = 222222L
    private val createdAt = LocalDateTime.of(2024, 1, 1, 12, 0)
    private val modifiedAt = LocalDateTime.of(2024, 6, 1, 12, 0)

    @BeforeEach
    internal fun createUser() {
      migrationService.migrateUser(
        UserMigrationRequest(
          user = MigratedUser(
            staffId = legacyStaffId,
            emails = listOf(
              migratedUserEmail("sync.user@example.org", 1),
              migratedUserEmail("sync.user@justice.gov.uk", 2),
            ),
            firstName = "Original",
            lastName = "Name",
            status = UserStatus.ACTIVE,
            createdTimestamp = createdAt,
            createdBy = "MIGRATION_TEST",
          ),
          accounts = listOf(
            migratedUserAccount(username = "SYNC_USER", activeCaseloadId = "LEI"),
            migratedUserAccount(username = "SYNC_USER_ADMIN", activeCaseloadId = "MDI"),
          ),
          roles = listOf(
            migratedUserRole(username = "SYNC_USER", roleCode = "ROLE_OLD_ONE"),
            migratedUserRole(username = "SYNC_USER", roleCode = "ROLE_OLD_TWO"),
            migratedUserRole(username = "SYNC_USER_ADMIN", roleCode = "ROLE_ADMIN_OLD"),
          ),
          accessibleCaseloads = listOf(
            migratedAccessibleCaseload(username = "SYNC_USER", caseloadId = "LEI"),
            migratedAccessibleCaseload(username = "SYNC_USER", caseloadId = "MDI"),
            migratedAccessibleCaseload(username = "SYNC_USER_ADMIN", caseloadId = "MDI"),
          ),
        ),
      )
    }

    @AfterEach
    internal fun deleteUsers() = dataBuilder.deleteAll()

    // ── Auth checks ──────────────────────────────────────────────────────────

    @Test
    fun `access unauthorized when no authority`() {
      webTestClient.put().uri("/sync/user/$legacyStaffId")
        .body(BodyInserters.fromValue(minimalSyncRequest()))
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.put().uri("/sync/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf()))
        .body(BodyInserters.fromValue(minimalSyncRequest()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.put().uri("/sync/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .body(BodyInserters.fromValue(minimalSyncRequest()))
        .exchange()
        .expectStatus().isForbidden
    }

    // ── Not found ────────────────────────────────────────────────────────────

    @Test
    fun `returns 404 when user does not exist`() {
      webTestClient.put().uri("/sync/user/999999")
        .headers(setAuthorisation(roles = listOf(SYNC_ROLE)))
        .body(BodyInserters.fromValue(minimalSyncRequest()))
        .exchange()
        .expectStatus().isNotFound
        .expectBody()
        .jsonPath("userMessage").isEqualTo("User not found: User with legacy staff id 999999 not found")
    }

    // ── Scalar field updates ─────────────────────────────────────────────────

    @Test
    fun `updates user scalar fields`() {
      webTestClient.put().uri("/sync/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf(SYNC_ROLE)))
        .body(
          BodyInserters.fromValue(
            minimalSyncRequest(
              firstName = "UpdatedFirst",
              lastName = "UpdatedLast",
              status = UserStatus.INACTIVE,
              modifiedTimestamp = modifiedAt,
              modifiedBy = "SYNC_TEST",
            ),
          ),
        )
        .exchange()
        .expectStatus().isNoContent

      webTestClient.get().uri("/reconciliation/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf(RECONCILIATION_ROLE)))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("firstName").isEqualTo("UpdatedFirst")
        .jsonPath("lastName").isEqualTo("UpdatedLast")
        .jsonPath("status").isEqualTo("INACTIVE")
    }

    // ── Email replacement ────────────────────────────────────────────────────

    @Test
    fun `replaces all emails with those in the request`() {
      webTestClient.put().uri("/sync/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf(SYNC_ROLE)))
        .body(
          BodyInserters.fromValue(
            minimalSyncRequest(
              emails = listOf(
                syncEmail("new.address@justice.gov.uk"),
              ),
            ),
          ),
        )
        .exchange()
        .expectStatus().isNoContent

      webTestClient.get().uri("/reconciliation/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf(RECONCILIATION_ROLE)))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("emails.length()").isEqualTo(1)
        .jsonPath("emails[0].email").isEqualTo("new.address@justice.gov.uk")
        .jsonPath("emails[0].isPrimary").isEqualTo(true)
    }

    @Test
    fun `marks justice gov uk email as primary when present`() {
      webTestClient.put().uri("/sync/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf(SYNC_ROLE)))
        .body(
          BodyInserters.fromValue(
            minimalSyncRequest(
              emails = listOf(
                syncEmail("other@example.org"),
                syncEmail("primary@justice.gov.uk"),
              ),
            ),
          ),
        )
        .exchange()
        .expectStatus().isNoContent

      webTestClient.get().uri("/reconciliation/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf(RECONCILIATION_ROLE)))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("emails.length()").isEqualTo(2)
        .jsonPath("emails[0].email").isEqualTo("primary@justice.gov.uk")
        .jsonPath("emails[0].isPrimary").isEqualTo(true)
        .jsonPath("emails[1].isPrimary").isEqualTo(false)
    }

    @Test
    fun `clears all emails when request has empty email list`() {
      webTestClient.put().uri("/sync/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf(SYNC_ROLE)))
        .body(BodyInserters.fromValue(minimalSyncRequest(emails = emptyList())))
        .exchange()
        .expectStatus().isNoContent

      webTestClient.get().uri("/reconciliation/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf(RECONCILIATION_ROLE)))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("emails.length()").isEqualTo(0)
    }

    // ── Account field updates ────────────────────────────────────────────────

    @Test
    fun `updates account scalar fields including lastLoggedIn`() {
      val lastLoggedIn = LocalDateTime.of(2024, 5, 10, 8, 30)

      webTestClient.put().uri("/sync/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf(SYNC_ROLE)))
        .body(
          BodyInserters.fromValue(
            minimalSyncRequest(
              accounts = listOf(
                syncAccount(
                  username = "SYNC_USER",
                  accountStatus = AccountStatus.LOCKED,
                  activeCaseloadId = "MDI",
                  lastLoggedIn = lastLoggedIn,
                ),
                syncAccount(username = "SYNC_USER_ADMIN", activeCaseloadId = "MDI"),
              ),
            ),
          ),
        )
        .exchange()
        .expectStatus().isNoContent

      webTestClient.get().uri("/reconciliation/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf(RECONCILIATION_ROLE)))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("accounts[0].username").isEqualTo("SYNC_USER")
        .jsonPath("accounts[0].accountStatus").isEqualTo("LOCKED")
        .jsonPath("accounts[0].activeCaseloadId").isEqualTo("MDI")
        .jsonPath("accounts[0].lastLoggedIn").isEqualTo("2024-05-10T08:30:00")
    }

    // ── Role replacement ─────────────────────────────────────────────────────

    @Test
    fun `replaces all roles on an account`() {
      webTestClient.put().uri("/sync/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf(SYNC_ROLE)))
        .body(
          BodyInserters.fromValue(
            minimalSyncRequest(
              accounts = listOf(
                syncAccount(
                  username = "SYNC_USER",
                  activeCaseloadId = "LEI",
                  roles = listOf(
                    syncRole("ROLE_NEW_ONE"),
                    syncRole("ROLE_NEW_TWO"),
                    syncRole("ROLE_NEW_THREE"),
                  ),
                ),
                syncAccount(username = "SYNC_USER_ADMIN", activeCaseloadId = "MDI"),
              ),
            ),
          ),
        )
        .exchange()
        .expectStatus().isNoContent

      webTestClient.get().uri("/reconciliation/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf(RECONCILIATION_ROLE)))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("accounts[0].username").isEqualTo("SYNC_USER")
        .jsonPath("accounts[0].roles.length()").isEqualTo(3)
        .jsonPath("accounts[0].roles[0].roleCode").isEqualTo("ROLE_NEW_ONE")
        .jsonPath("accounts[0].roles[1].roleCode").isEqualTo("ROLE_NEW_THREE") // H < W alphabetically
        .jsonPath("accounts[0].roles[2].roleCode").isEqualTo("ROLE_NEW_TWO")
        // OLD roles should be gone
        .jsonPath("accounts[1].username").isEqualTo("SYNC_USER_ADMIN")
        .jsonPath("accounts[1].roles.length()").isEqualTo(0)
    }

    // ── Accessible caseload replacement ──────────────────────────────────────

    @Test
    fun `replaces all accessible caseloads on an account`() {
      webTestClient.put().uri("/sync/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf(SYNC_ROLE)))
        .body(
          BodyInserters.fromValue(
            minimalSyncRequest(
              accounts = listOf(
                syncAccount(
                  username = "SYNC_USER",
                  activeCaseloadId = "WWI",
                  caseloads = listOf(
                    syncCaseload("WWI"),
                  ),
                ),
                syncAccount(username = "SYNC_USER_ADMIN", activeCaseloadId = "MDI"),
              ),
            ),
          ),
        )
        .exchange()
        .expectStatus().isNoContent

      webTestClient.get().uri("/reconciliation/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf(RECONCILIATION_ROLE)))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("accounts[0].username").isEqualTo("SYNC_USER")
        .jsonPath("accounts[0].caseloads.length()").isEqualTo(1)
        .jsonPath("accounts[0].caseloads[0].caseloadId").isEqualTo("WWI")
    }

    // ── Account removal ──────────────────────────────────────────────────────

    @Test
    fun `removes accounts absent from the request`() {
      webTestClient.put().uri("/sync/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf(SYNC_ROLE)))
        .body(
          BodyInserters.fromValue(
            minimalSyncRequest(
              accounts = listOf(
                // Only keep SYNC_USER_ADMIN; SYNC_USER should be removed
                syncAccount(username = "SYNC_USER_ADMIN", activeCaseloadId = "MDI"),
              ),
            ),
          ),
        )
        .exchange()
        .expectStatus().isNoContent

      webTestClient.get().uri("/reconciliation/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf(RECONCILIATION_ROLE)))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("accounts.length()").isEqualTo(1)
        .jsonPath("accounts[0].username").isEqualTo("SYNC_USER_ADMIN")
    }

    // ── Account creation ─────────────────────────────────────────────────────

    @Test
    fun `creates new accounts present in the request but not in the database`() {
      webTestClient.put().uri("/sync/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf(SYNC_ROLE)))
        .body(
          BodyInserters.fromValue(
            minimalSyncRequest(
              accounts = listOf(
                syncAccount(username = "SYNC_USER", activeCaseloadId = "LEI"),
                syncAccount(username = "SYNC_USER_ADMIN", activeCaseloadId = "MDI"),
                syncAccount(
                  username = "SYNC_USER_NEW",
                  activeCaseloadId = "WWI",
                  caseloads = listOf(syncCaseload("WWI")),
                  roles = listOf(syncRole("ROLE_BRAND_NEW")),
                ),
              ),
            ),
          ),
        )
        .exchange()
        .expectStatus().isNoContent

      webTestClient.get().uri("/reconciliation/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf(RECONCILIATION_ROLE)))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("accounts.length()").isEqualTo(3)
        .jsonPath("accounts[2].username").isEqualTo("SYNC_USER_NEW")
        .jsonPath("accounts[2].activeCaseloadId").isEqualTo("WWI")
        .jsonPath("accounts[2].caseloads.length()").isEqualTo(1)
        .jsonPath("accounts[2].caseloads[0].caseloadId").isEqualTo("WWI")
        .jsonPath("accounts[2].roles.length()").isEqualTo(1)
        .jsonPath("accounts[2].roles[0].roleCode").isEqualTo("ROLE_BRAND_NEW")
    }

    // ── Caseload not found ───────────────────────────────────────────────────

    @Test
    fun `returns 404 when a caseload in the request does not exist`() {
      webTestClient.put().uri("/sync/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf(SYNC_ROLE)))
        .body(
          BodyInserters.fromValue(
            minimalSyncRequest(
              accounts = listOf(
                syncAccount(
                  username = "SYNC_USER",
                  activeCaseloadId = "LEI",
                  caseloads = listOf(syncCaseload("DOES_NOT_EXIST")),
                ),
              ),
            ),
          ),
        )
        .exchange()
        .expectStatus().isNotFound
        .expectBody()
        .jsonPath("userMessage").value<String> { msg ->
          assert(msg.contains("DOES_NOT_EXIST")) { "Expected message to mention the missing caseload but was: $msg" }
        }
    }
  }

  // ── Builder helpers ──────────────────────────────────────────────────────

  private fun minimalSyncRequest(
    firstName: String = "Original",
    lastName: String = "Name",
    status: UserStatus = UserStatus.ACTIVE,
    modifiedTimestamp: LocalDateTime? = null,
    modifiedBy: String? = null,
    emails: List<SyncPrisonUserEmail> = listOf(
      syncEmail("sync.user@example.org"),
      syncEmail("sync.user@justice.gov.uk"),
    ),
    accounts: List<SyncPrisonUserAccount> = listOf(
      syncAccount(username = "SYNC_USER", activeCaseloadId = "LEI"),
      syncAccount(username = "SYNC_USER_ADMIN", activeCaseloadId = "MDI"),
    ),
  ) = PrisonUserSyncRequest(
    firstName = firstName,
    lastName = lastName,
    status = status,
    createdTimestamp = LocalDateTime.of(2024, 1, 1, 12, 0),
    createdBy = "SYNC_TEST",
    modifiedTimestamp = modifiedTimestamp,
    modifiedBy = modifiedBy,
    emails = emails,
    accounts = accounts,
  )

  private fun syncEmail(email: String) = SyncPrisonUserEmail(
    email = email,
    createdTimestamp = LocalDateTime.of(2024, 1, 1, 12, 0),
    createdBy = "SYNC_TEST",
  )

  private fun syncAccount(
    username: String,
    accountType: UsageType = UsageType.GENERAL,
    accountStatus: AccountStatus = AccountStatus.OPEN,
    activeCaseloadId: String? = null,
    lastLoggedIn: LocalDateTime? = null,
    caseloads: List<SyncPrisonUserCaseload> = emptyList(),
    roles: List<SyncPrisonUserRole> = emptyList(),
  ) = SyncPrisonUserAccount(
    username = username,
    accountType = accountType,
    accountStatus = accountStatus,
    activeCaseloadId = activeCaseloadId,
    lastLoggedIn = lastLoggedIn,
    caseloads = caseloads,
    roles = roles,
    createdTimestamp = LocalDateTime.of(2024, 1, 1, 12, 0),
    createdBy = "SYNC_TEST",
  )

  private fun syncCaseload(caseloadId: String) = SyncPrisonUserCaseload(
    caseloadId = caseloadId,
    createdTimestamp = LocalDateTime.of(2024, 1, 1, 12, 0),
    createdBy = "SYNC_TEST",
  )

  private fun syncRole(roleCode: String) = SyncPrisonUserRole(
    roleCode = roleCode,
    createdTimestamp = LocalDateTime.of(2024, 1, 1, 12, 0),
    createdBy = "SYNC_TEST",
  )

  private fun migratedUserAccount(username: String, activeCaseloadId: String) = MigratedUserAccount(
    username = username,
    accountType = UsageType.GENERAL,
    accountStatus = AccountStatus.OPEN,
    activeCaseloadId = activeCaseloadId,
    createdTimestamp = LocalDateTime.of(2024, 1, 1, 12, 0),
    createdBy = "MIGRATION_TEST",
  )

  private fun migratedUserEmail(email: String, legacyEmailId: Long) = MigratedUserEmail(
    email = email,
    legacyEmailId = legacyEmailId,
    createdTimestamp = LocalDateTime.of(2024, 1, 1, 12, 0),
    createdBy = "MIGRATION_TEST",
  )

  private fun migratedAccessibleCaseload(username: String, caseloadId: String) = MigratedUserAccessibleCaseload(
    username = username,
    caseloadId = caseloadId,
    createdTimestamp = LocalDateTime.of(2024, 1, 1, 12, 0),
    createdBy = "MIGRATION_TEST",
  )

  private fun migratedUserRole(username: String, roleCode: String) = MigratedUserRole(
    username = username,
    roleCode = roleCode,
    createdTimestamp = LocalDateTime.of(2024, 1, 1, 12, 0),
    createdBy = "MIGRATION_TEST",
  )
}
