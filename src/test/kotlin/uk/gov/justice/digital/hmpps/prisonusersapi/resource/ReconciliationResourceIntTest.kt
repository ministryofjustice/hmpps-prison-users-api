package uk.gov.justice.digital.hmpps.prisonusersapi.resource

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonusersapi.data.AccountStatus
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UsageType
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserStatus
import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.PrisonUserSyncRequest
import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.SyncPrisonUserAccount
import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.SyncPrisonUserCaseload
import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.SyncPrisonUserEmail
import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.SyncPrisonUserRole
import uk.gov.justice.digital.hmpps.prisonusersapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonusersapi.integration.helper.DataBuilder
import uk.gov.justice.digital.hmpps.prisonusersapi.service.SyncService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ReconciliationResourceIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var syncService: SyncService

  @Autowired
  private lateinit var dataBuilder: DataBuilder

  @DisplayName("GET /reconciliation/user/{legacyStaffId}")
  @Nested
  inner class GetPrisonUserForReconciliation {
    private val legacyStaffId = 111111L

    @BeforeEach
    internal fun createUser() {
      syncService.syncUser(
        legacyStaffId,
        PrisonUserSyncRequest(
          firstName = "Recon",
          lastName = "User",
          status = UserStatus.ACTIVE,
          createdTimestamp = LocalDateTime.now(),
          createdBy = "MIGRATION_TEST",
          emails = listOf(
            syncPrisonUserEmail("person@example.org"),
            syncPrisonUserEmail("person@justice.gov.uk"),
          ),
          accounts = listOf(
            userAccount(
              username = "RECON_USER",
              activeCaseloadId = "LEI",
              caseloads = listOf(
                accessibleCaseload(caseloadId = "LEI"),
                accessibleCaseload(caseloadId = "MDI"),
              ),
              roles = listOf(
                userRole(roleCode = "ROLE_ALPHA"),
                userRole(roleCode = "ROLE_BRAVO"),
              ),
            ),
            userAccount(
              username = "RECON_USER_2",
              activeCaseloadId = "MDI",
              caseloads = listOf(
                accessibleCaseload(caseloadId = "MDI"),
              ),
              roles = listOf(
                userRole(roleCode = "ROLE_CHARLIE"),
              ),
            ),
          ),
        ),
      )
    }

    @AfterEach
    internal fun deleteUsers() = dataBuilder.deleteAll()

    @Test
    fun `access unauthorized when no authority`() {
      webTestClient.get().uri("/reconciliation/user/$legacyStaffId")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.get().uri("/reconciliation/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.get().uri("/reconciliation/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `user not found`() {
      webTestClient.get().uri("/reconciliation/user/999999")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
        .exchange()
        .expectStatus().isNotFound
        .expectBody()
        .jsonPath("userMessage").isEqualTo("User not found: User with legacy staff id 999999 not found")
    }

    @Test
    fun `get full reconciliation user payload`() {
      webTestClient.get().uri("/reconciliation/user/$legacyStaffId")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("userId").exists()
        .jsonPath("staffId").isEqualTo(legacyStaffId)
        .jsonPath("firstName").isEqualTo("Recon")
        .jsonPath("lastName").isEqualTo("User")
        .jsonPath("status").isEqualTo("ACTIVE")
        .jsonPath("emails.length()").isEqualTo(2)
        .jsonPath("emails[0].email").isEqualTo("person@justice.gov.uk")
        .jsonPath("emails[0].isPrimary").isEqualTo(true)
        .jsonPath("accounts.length()").isEqualTo(2)
        .jsonPath("accounts[0].username").isEqualTo("RECON_USER")
        .jsonPath("accounts[0].lastLoggedIn").isEqualTo(LocalDateTime.of(2022, 1, 1, 1, 1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        .jsonPath("accounts[0].roles.length()").isEqualTo(2)
        .jsonPath("accounts[0].roles[0].roleCode").isEqualTo("ROLE_ALPHA")
        .jsonPath("accounts[0].roles[1].roleCode").isEqualTo("ROLE_BRAVO")
        .jsonPath("accounts[0].caseloads.length()").isEqualTo(2)
        .jsonPath("accounts[0].caseloads[0].caseloadId").isEqualTo("LEI")
        .jsonPath("accounts[0].caseloads[1].caseloadId").isEqualTo("MDI")
        .jsonPath("accounts[1].username").isEqualTo("RECON_USER_2")
        .jsonPath("accounts[1].roles[0].roleCode").isEqualTo("ROLE_CHARLIE")
        .jsonPath("accounts[1].activeCaseloadId").isEqualTo("MDI")
    }
  }

  private fun userAccount(username: String, activeCaseloadId: String, caseloads: List<SyncPrisonUserCaseload> = listOf(), roles: List<SyncPrisonUserRole> = listOf()) = SyncPrisonUserAccount(
    username = username,
    accountType = UsageType.GENERAL,
    accountStatus = AccountStatus.OPEN,
    activeCaseloadId = activeCaseloadId,
    lastLoggedIn = LocalDateTime.of(2022, 1, 1, 1, 1),
    createdTimestamp = LocalDateTime.now(),
    createdBy = "MIGRATION_TEST",
    caseloads = caseloads,
    roles = roles,
  )

  private fun syncPrisonUserEmail(email: String) = SyncPrisonUserEmail(
    email = email,
    createdTimestamp = LocalDateTime.now(),
    createdBy = "MIGRATION_TEST",
  )

  private fun accessibleCaseload(caseloadId: String) = SyncPrisonUserCaseload(
    caseloadId = caseloadId,
    createdTimestamp = LocalDateTime.now(),
    createdBy = "MIGRATION_TEST",
  )

  private fun userRole(roleCode: String) = SyncPrisonUserRole(
    roleCode = roleCode,
    createdTimestamp = LocalDateTime.now(),
    createdBy = "MIGRATION_TEST",
  )
}
