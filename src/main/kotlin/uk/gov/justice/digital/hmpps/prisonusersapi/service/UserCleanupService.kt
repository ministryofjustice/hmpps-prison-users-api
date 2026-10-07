package uk.gov.justice.digital.hmpps.prisonusersapi.service

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Service
class UserCleanupService(
  private val jdbcTemplate: NamedParameterJdbcTemplate,
) {

  @Scheduled(cron = "0 0 0 1 * *")
  @Transactional
  fun runMonthlyCleanup() {
    val staleUserIds = findStaleUserIds()
    if (staleUserIds.isEmpty()) {
      log.info("No inactive users with audit revisions older than 7 years found")
      return
    }

    log.info("Found {} inactive users with audit revisions older than 7 years", staleUserIds.size)
    purgeStaleUserData(staleUserIds)
  }

  private fun findStaleUserIds(): List<UUID> {
    val cutoffMillis = Instant.now().atZone(ZoneOffset.UTC).minusYears(7).toInstant().toEpochMilli()
    return jdbcTemplate.query(
      """
      SELECT ua.user_id
      FROM users_audit ua
      JOIN revinfo r ON r.rev = ua.rev
      WHERE ua.status = 'INACTIVE'
      AND ua.rev = (
        SELECT MAX(ua2.rev)
        FROM users_audit ua2
        WHERE ua2.user_id = ua.user_id
      )
      AND r.revtstmp < :cutoffMillis
      """.trimIndent(),
      mapOf("cutoffMillis" to cutoffMillis),
    ) { rs, _ ->
      UUID.fromString(rs.getString("user_id"))
    }
  }

  private fun findUsernamesForUserIds(userIds: Collection<UUID>): List<String> {
    if (userIds.isEmpty()) {
      return emptyList()
    }

    val usernames = jdbcTemplate.queryForList(
      """
      SELECT DISTINCT username
      FROM user_account_audit
      WHERE user_id IN (:userIds)
      """.trimIndent(),
      mapOf("userIds" to userIds.toList()),
      String::class.java,
    )
    return usernames.filterNotNull()
  }

  private fun deleteAuditData(userIds: Collection<UUID>, usernames: Collection<String>) {
    if (userIds.isNotEmpty()) {
      jdbcTemplate.update("DELETE FROM users_audit WHERE user_id IN (:userIds)", mapOf("userIds" to userIds.toList()))
      jdbcTemplate.update("DELETE FROM user_account_audit WHERE user_id IN (:userIds)", mapOf("userIds" to userIds.toList()))
      jdbcTemplate.update("DELETE FROM user_emails_audit WHERE user_id IN (:userIds)", mapOf("userIds" to userIds.toList()))
    }

    if (usernames.isNotEmpty()) {
      val usernameList = usernames.toList()
      jdbcTemplate.update("DELETE FROM user_roles_audit WHERE username IN (:usernames)", mapOf("usernames" to usernameList))
      jdbcTemplate.update("DELETE FROM user_accessible_caseloads_audit WHERE username IN (:usernames)", mapOf("usernames" to usernameList))
      jdbcTemplate.update("DELETE FROM user_caseload_administrators_audit WHERE username IN (:usernames)", mapOf("usernames" to usernameList))
      jdbcTemplate.update("DELETE FROM user_caseload_members_audit WHERE username IN (:usernames)", mapOf("usernames" to usernameList))
    }

    deleteOrphanedRevinfo()
  }

  private fun deleteOrphanedRevinfo() {
    val referencedRevisions = findReferencedRevisions()
    if (referencedRevisions.isEmpty()) {
      jdbcTemplate.update("DELETE FROM revinfo", emptyMap<String, Any?>())
    } else {
      jdbcTemplate.update(
        "DELETE FROM revinfo WHERE rev NOT IN (:revisions)",
        mapOf("revisions" to referencedRevisions),
      )
    }
  }

  private fun findReferencedRevisions(): List<Long> = jdbcTemplate.queryForList(
    """
      SELECT DISTINCT rev FROM users_audit
      UNION
      SELECT DISTINCT rev FROM user_account_audit
      UNION
      SELECT DISTINCT rev FROM user_emails_audit
      UNION
      SELECT DISTINCT rev FROM user_roles_audit
      UNION
      SELECT DISTINCT rev FROM user_accessible_caseloads_audit
      UNION
      SELECT DISTINCT rev FROM user_caseload_administrators_audit
      UNION
      SELECT DISTINCT rev FROM user_caseload_members_audit
    """.trimIndent(),
    emptyMap<String, Any?>(),
    Long::class.java,
  ).filterNotNull()

  private fun deleteActualUserData(userIds: Collection<UUID>, usernames: Collection<String>) {
    if (usernames.isNotEmpty()) {
      val usernameList = usernames.toList()
      jdbcTemplate.update("DELETE FROM user_caseload_administrators WHERE username IN (:usernames)", mapOf("usernames" to usernameList))
      jdbcTemplate.update("DELETE FROM user_caseload_members WHERE username IN (:usernames)", mapOf("usernames" to usernameList))
      jdbcTemplate.update("DELETE FROM user_accessible_caseloads WHERE username IN (:usernames)", mapOf("usernames" to usernameList))
      jdbcTemplate.update("DELETE FROM user_roles WHERE username IN (:usernames)", mapOf("usernames" to usernameList))
      jdbcTemplate.update("DELETE FROM user_account WHERE username IN (:usernames)", mapOf("usernames" to usernameList))
    }

    if (userIds.isNotEmpty()) {
      jdbcTemplate.update("DELETE FROM user_emails WHERE user_id IN (:userIds)", mapOf("userIds" to userIds.toList()))
      jdbcTemplate.update("DELETE FROM users WHERE user_id IN (:userIds)", mapOf("userIds" to userIds.toList()))
    }
  }

  private fun purgeStaleUserData(staleUserIds: List<UUID>) {
    val staleUsernames = findUsernamesForUserIds(staleUserIds)

    deleteAuditData(staleUserIds, staleUsernames)
    deleteActualUserData(staleUserIds, staleUsernames)
  }

  companion object {
    private val log = LoggerFactory.getLogger(UserCleanupService::class.java)
  }
}
