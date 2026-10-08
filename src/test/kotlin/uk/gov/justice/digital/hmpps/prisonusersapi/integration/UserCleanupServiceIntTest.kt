package uk.gov.justice.digital.hmpps.prisonusersapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.annotation.DirtiesContext
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserStatus
import uk.gov.justice.digital.hmpps.prisonusersapi.service.UserCleanupService
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserCleanupServiceIntTest : IntegrationTestBase() {

  private val cleanupLockName = "UserCleanupService.runMonthlyCleanup"

  private val insertedRevisionIds = mutableSetOf<Long>()

  @Autowired
  private lateinit var userCleanupService: UserCleanupService

  @Autowired
  private lateinit var jdbcTemplate: NamedParameterJdbcTemplate

  @BeforeEach
  fun setUp() {
    jdbcTemplate.update("DELETE FROM scheduled_job_lock WHERE name = :name", mapOf("name" to cleanupLockName))
  }

  @AfterEach
  fun tearDown() {
    jdbcTemplate.update("DELETE FROM scheduled_job_lock WHERE name = :name", mapOf("name" to cleanupLockName))
    jdbcTemplate.update("DELETE FROM user_caseload_members_audit", emptyMap<String, Any?>())
    jdbcTemplate.update("DELETE FROM user_caseload_administrators_audit", emptyMap<String, Any?>())
    jdbcTemplate.update("DELETE FROM user_accessible_caseloads_audit", emptyMap<String, Any?>())
    jdbcTemplate.update("DELETE FROM user_roles_audit", emptyMap<String, Any?>())
    jdbcTemplate.update("DELETE FROM user_emails_audit", emptyMap<String, Any?>())
    jdbcTemplate.update("DELETE FROM user_account_audit", emptyMap<String, Any?>())
    jdbcTemplate.update("DELETE FROM users_audit", emptyMap<String, Any?>())
    jdbcTemplate.update("DELETE FROM user_caseload_members", emptyMap<String, Any?>())
    jdbcTemplate.update("DELETE FROM user_caseload_administrators", emptyMap<String, Any?>())
    jdbcTemplate.update("DELETE FROM user_accessible_caseloads", emptyMap<String, Any?>())
    jdbcTemplate.update("DELETE FROM user_roles", emptyMap<String, Any?>())
    jdbcTemplate.update("DELETE FROM user_emails", emptyMap<String, Any?>())
    jdbcTemplate.update("DELETE FROM user_account", emptyMap<String, Any?>())
    jdbcTemplate.update("DELETE FROM users", emptyMap<String, Any?>())

    if (insertedRevisionIds.isNotEmpty()) {
      jdbcTemplate.update("DELETE FROM revinfo WHERE rev IN (:revisions)", mapOf("revisions" to insertedRevisionIds.toList()))
      insertedRevisionIds.clear()
    }
  }

  @Test
  fun `runMonthlyCleanup removes stale inactive user audit data and associated user records`() {
    val userId = UUID.randomUUID()
    val username = "stale-user-inactive"
    insertStaleUser(userId, username, UserStatus.INACTIVE)

    userCleanupService.runMonthlyCleanup()

    assertThat(countRows("users", "user_id", userId)).isZero()
    assertThat(countRows("user_account", "username", username)).isZero()
    assertThat(countRows("user_emails", "user_id", userId)).isZero()
    assertThat(countRows("user_roles", "username", username)).isZero()
    assertThat(countRows("user_accessible_caseloads", "username", username)).isZero()
    assertThat(countRows("user_caseload_administrators", "username", username)).isZero()
    assertThat(countRows("user_caseload_members", "username", username)).isZero()

    assertThat(countAuditRows("users_audit", "user_id", userId)).isZero()
    assertThat(countAuditRows("user_account_audit", "user_id", userId)).isZero()
    assertThat(countAuditRows("user_emails_audit", "user_id", userId)).isZero()
    assertThat(countAuditRows("user_roles_audit", "username", username)).isZero()
    assertThat(countAuditRows("user_accessible_caseloads_audit", "username", username)).isZero()
    assertThat(countAuditRows("user_caseload_administrators_audit", "username", username)).isZero()
    assertThat(countAuditRows("user_caseload_members_audit", "username", username)).isZero()
  }

  @Test
  fun `runMonthlyCleanup ignores stale users that are not inactive`() {
    val userId = UUID.randomUUID()
    val username = "active-stale-user"
    insertStaleUser(userId, username, UserStatus.ACTIVE)

    userCleanupService.runMonthlyCleanup()

    assertThat(countRows("users", "user_id", userId)).isEqualTo(1)
    assertThat(countRows("user_account", "username", username)).isEqualTo(1)
    assertThat(countRows("user_emails", "user_id", userId)).isEqualTo(1)
    assertThat(countRows("user_roles", "username", username)).isEqualTo(1)
    assertThat(countRows("user_accessible_caseloads", "username", username)).isEqualTo(1)
    assertThat(countRows("user_caseload_administrators", "username", username)).isEqualTo(1)
    assertThat(countRows("user_caseload_members", "username", username)).isEqualTo(1)

    assertThat(
      jdbcTemplate.queryForObject(
        "SELECT status FROM users WHERE user_id = :userId",
        mapOf("userId" to userId),
        String::class.java,
      ),
    ).isEqualTo(UserStatus.ACTIVE.name)

    assertThat(countAuditRows("users_audit", "user_id", userId)).isEqualTo(1)
    assertThat(countAuditRows("user_account_audit", "user_id", userId)).isEqualTo(1)
    assertThat(countAuditRows("user_emails_audit", "user_id", userId)).isEqualTo(1)
    assertThat(countAuditRows("user_roles_audit", "username", username)).isEqualTo(1)
    assertThat(countAuditRows("user_accessible_caseloads_audit", "username", username)).isEqualTo(1)
    assertThat(countAuditRows("user_caseload_administrators_audit", "username", username)).isEqualTo(1)
    assertThat(countAuditRows("user_caseload_members_audit", "username", username)).isEqualTo(1)
  }

  @Test
  fun `runMonthlyCleanup removes stale inactive audit data when live rows are already absent`() {
    val userId = UUID.randomUUID()
    val username = "stale-audit-only-user"
    insertStaleUser(userId, username, UserStatus.INACTIVE, includeLiveData = false)

    userCleanupService.runMonthlyCleanup()

    assertThat(countRows("users", "user_id", userId)).isZero()
    assertThat(countRows("user_account", "username", username)).isZero()
    assertThat(countRows("user_emails", "user_id", userId)).isZero()
    assertThat(countRows("user_roles", "username", username)).isZero()
    assertThat(countRows("user_accessible_caseloads", "username", username)).isZero()
    assertThat(countRows("user_caseload_administrators", "username", username)).isZero()
    assertThat(countRows("user_caseload_members", "username", username)).isZero()

    assertThat(countAuditRows("users_audit", "user_id", userId)).isZero()
    assertThat(countAuditRows("user_account_audit", "user_id", userId)).isZero()
    assertThat(countAuditRows("user_emails_audit", "user_id", userId)).isZero()
    assertThat(countAuditRows("user_roles_audit", "username", username)).isZero()
    assertThat(countAuditRows("user_accessible_caseloads_audit", "username", username)).isZero()
    assertThat(countAuditRows("user_caseload_administrators_audit", "username", username)).isZero()
    assertThat(countAuditRows("user_caseload_members_audit", "username", username)).isZero()
  }

  private fun insertStaleUser(
    userId: UUID,
    username: String,
    status: UserStatus,
    includeLiveData: Boolean = true,
  ) {
    val staleRevisionTs = Instant.now().minusSeconds(8L * 365 * 24 * 60 * 60).toEpochMilli()
    val staleTimestamp = LocalDateTime.now().minusSeconds(8L * 365 * 24 * 60 * 60)
    val rev = nextRevisionNumber()
    val caseloadId = "LEI"
    val auditEmailId = nextAuditEmailId()
    insertedRevisionIds.add(rev)
    jdbcTemplate.update(
      "INSERT INTO revinfo (rev, revtstmp) VALUES (:rev, :revtstmp)",
      mapOf("rev" to rev, "revtstmp" to staleRevisionTs),
    )

    if (includeLiveData) {
      jdbcTemplate.update(
        "INSERT INTO users (user_id, first_name, last_name, status, legacy_staff_id, created_timestamp, created_by) VALUES (:userId, :firstName, :lastName, :status, :legacyStaffId, :createdTimestamp, :createdBy)",
        mapOf(
          "userId" to userId,
          "firstName" to "Stale",
          "lastName" to "User",
          "status" to status.name,
          "legacyStaffId" to 100000L + userId.leastSignificantBits,
          "createdTimestamp" to staleTimestamp,
          "createdBy" to "test",
        ),
      )
    }

    jdbcTemplate.update(
      "INSERT INTO users_audit (user_id, first_name, last_name, status, legacy_staff_id, created_timestamp, created_by, rev, revtype) VALUES (:userId, :firstName, :lastName, :status, :legacyStaffId, :createdTimestamp, :createdBy, :rev, :revType)",
      mapOf(
        "userId" to userId,
        "firstName" to "Stale",
        "lastName" to "User",
        "status" to status.name,
        "legacyStaffId" to 100000L + userId.leastSignificantBits,
        "createdTimestamp" to staleTimestamp,
        "createdBy" to "test",
        "rev" to rev,
        "revType" to 0,
      ),
    )

    if (includeLiveData) {
      jdbcTemplate.update(
        "INSERT INTO user_account (user_id, username, account_type, account_status, active_caseload_id, created_timestamp, created_by) VALUES (:userId, :username, :accountType, :accountStatus, :activeCaseloadId, :createdTimestamp, :createdBy)",
        mapOf(
          "userId" to userId,
          "username" to username,
          "accountType" to "GENERAL",
          "accountStatus" to "OPEN",
          "activeCaseloadId" to caseloadId,
          "createdTimestamp" to staleTimestamp,
          "createdBy" to "test",
        ),
      )
    }

    jdbcTemplate.update(
      "INSERT INTO user_account_audit (username, user_id, account_type, account_status, active_caseload_id, created_timestamp, created_by, rev, revtype) VALUES (:username, :userId, :accountType, :accountStatus, :activeCaseloadId, :createdTimestamp, :createdBy, :rev, :revType)",
      mapOf(
        "username" to username,
        "userId" to userId,
        "accountType" to "GENERAL",
        "accountStatus" to "OPEN",
        "activeCaseloadId" to caseloadId,
        "createdTimestamp" to staleTimestamp,
        "createdBy" to "test",
        "rev" to rev,
        "revType" to 0,
      ),
    )

    if (includeLiveData) {
      jdbcTemplate.update(
        "INSERT INTO user_emails (user_id, email, is_primary, created_timestamp, created_by) VALUES (:userId, :email, true, :createdTimestamp, :createdBy)",
        mapOf(
          "userId" to userId,
          "email" to "$username@example.org",
          "createdTimestamp" to staleTimestamp,
          "createdBy" to "test",
        ),
      )
    }
    jdbcTemplate.update(
      "INSERT INTO user_emails_audit (id, user_id, email, is_primary, created_timestamp, created_by, rev, revtype) VALUES (:id, :userId, :email, true, :createdTimestamp, :createdBy, :rev, :revType)",
      mapOf(
        "id" to auditEmailId,
        "userId" to userId,
        "email" to "$username@example.org",
        "createdTimestamp" to staleTimestamp,
        "createdBy" to "test",
        "rev" to rev,
        "revType" to 0,
      ),
    )

    if (includeLiveData) {
      jdbcTemplate.update(
        "INSERT INTO user_roles (username, role_code, created_timestamp, created_by) VALUES (:username, :roleCode, :createdTimestamp, :createdBy)",
        mapOf(
          "username" to username,
          "roleCode" to "ROLE_STALE",
          "createdTimestamp" to staleTimestamp,
          "createdBy" to "test",
        ),
      )
    }
    jdbcTemplate.update(
      "INSERT INTO user_roles_audit (username, role_code, created_timestamp, created_by, rev, revtype) VALUES (:username, :roleCode, :createdTimestamp, :createdBy, :rev, :revType)",
      mapOf(
        "username" to username,
        "roleCode" to "ROLE_STALE",
        "createdTimestamp" to staleTimestamp,
        "createdBy" to "test",
        "rev" to rev,
        "revType" to 0,
      ),
    )

    if (includeLiveData) {
      jdbcTemplate.update(
        "INSERT INTO user_accessible_caseloads (username, caseload_id, created_timestamp, created_by) VALUES (:username, :caseloadId, :createdTimestamp, :createdBy)",
        mapOf(
          "username" to username,
          "caseloadId" to caseloadId,
          "createdTimestamp" to staleTimestamp,
          "createdBy" to "test",
        ),
      )
    }
    jdbcTemplate.update(
      "INSERT INTO user_accessible_caseloads_audit (username, caseload_id, created_timestamp, created_by, rev, revtype) VALUES (:username, :caseloadId, :createdTimestamp, :createdBy, :rev, :revType)",
      mapOf(
        "username" to username,
        "caseloadId" to caseloadId,
        "createdTimestamp" to staleTimestamp,
        "createdBy" to "test",
        "rev" to rev,
        "revType" to 0,
      ),
    )

    if (includeLiveData) {
      jdbcTemplate.update(
        "INSERT INTO user_caseload_administrators (username, caseload_id, active, created_timestamp, created_by) VALUES (:username, :caseloadId, true, :createdTimestamp, :createdBy)",
        mapOf(
          "username" to username,
          "caseloadId" to caseloadId,
          "createdTimestamp" to staleTimestamp,
          "createdBy" to "test",
        ),
      )
    }
    jdbcTemplate.update(
      "INSERT INTO user_caseload_administrators_audit (username, caseload_id, active, created_timestamp, created_by, rev, revtype) VALUES (:username, :caseloadId, true, :createdTimestamp, :createdBy, :rev, :revType)",
      mapOf(
        "username" to username,
        "caseloadId" to caseloadId,
        "createdTimestamp" to staleTimestamp,
        "createdBy" to "test",
        "rev" to rev,
        "revType" to 0,
      ),
    )

    if (includeLiveData) {
      jdbcTemplate.update(
        "INSERT INTO user_caseload_members (username, caseload_id, active, created_timestamp, created_by) VALUES (:username, :caseloadId, true, :createdTimestamp, :createdBy)",
        mapOf(
          "username" to username,
          "caseloadId" to caseloadId,
          "createdTimestamp" to staleTimestamp,
          "createdBy" to "test",
        ),
      )
    }
    jdbcTemplate.update(
      "INSERT INTO user_caseload_members_audit (username, caseload_id, active, created_timestamp, created_by, rev, revtype) VALUES (:username, :caseloadId, true, :createdTimestamp, :createdBy, :rev, :revType)",
      mapOf(
        "username" to username,
        "caseloadId" to caseloadId,
        "createdTimestamp" to staleTimestamp,
        "createdBy" to "test",
        "rev" to rev,
        "revType" to 0,
      ),
    )
  }

  private fun nextRevisionNumber(): Long = jdbcTemplate.queryForObject(
    "SELECT COALESCE(MAX(rev), 0) + 1 FROM revinfo",
    emptyMap<String, Any?>(),
    Long::class.java,
  ) ?: 1L

  private fun nextAuditEmailId(): Long = jdbcTemplate.queryForObject(
    "SELECT COALESCE(MAX(id), 0) + 1 FROM user_emails_audit",
    emptyMap<String, Any?>(),
    Long::class.java,
  ) ?: 1L

  private fun countRows(tableName: String, columnName: String, value: Any): Int = jdbcTemplate.queryForObject(
    "SELECT COUNT(*) FROM $tableName WHERE $columnName = :value",
    mapOf("value" to value),
    Int::class.java,
  ) ?: 0

  private fun countAuditRows(tableName: String, columnName: String, value: Any): Int = jdbcTemplate.queryForObject(
    "SELECT COUNT(*) FROM $tableName WHERE $columnName = :value",
    mapOf("value" to value),
    Int::class.java,
  ) ?: 0
}
