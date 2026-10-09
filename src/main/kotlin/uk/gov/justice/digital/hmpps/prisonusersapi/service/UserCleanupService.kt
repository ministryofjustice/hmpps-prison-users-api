package uk.gov.justice.digital.hmpps.prisonusersapi.service

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Service
class UserCleanupService(
  private val jdbcTemplate: NamedParameterJdbcTemplate,
  transactionManager: PlatformTransactionManager,
  @Value($$"${user-cleanup.batch-size:100}") private val batchSize: Int,
) {

  private val transactionTemplate = TransactionTemplate(transactionManager)

  init {
    require(batchSize > 0) { "user-cleanup.batch-size must be greater than zero" }
  }

  @Scheduled(cron = "0 0 0 1 * *")
  @SchedulerLock(name = "UserCleanupService.cleanup")
  fun cleanup() {
    val cutoffMillis = Instant.now().atZone(ZoneOffset.UTC).minusYears(7).toInstant().toEpochMilli()
    var totalPurgedUsers = 0

    while (true) {
      val purgedUsersInBatch = transactionTemplate.execute {
        purgeNextBatch(cutoffMillis)
      }

      if (purgedUsersInBatch == 0) {
        break
      }

      totalPurgedUsers += purgedUsersInBatch
      log.info(
        "Purged batch of {} inactive users with audit revisions older than 7 years (total purged so far: {})",
        purgedUsersInBatch,
        totalPurgedUsers,
      )
    }

    if (totalPurgedUsers == 0) {
      log.info("No inactive users with audit revisions older than 7 years found")
      return
    }

    log.info(
      "Completed monthly cleanup for {} inactive users with audit revisions older than 7 years using batches of up to {}",
      totalPurgedUsers,
      batchSize,
    )
  }

  private fun purgeNextBatch(cutoffMillis: Long): Int {
    val staleUserIds = findStaleUserIds(cutoffMillis)
    if (staleUserIds.isEmpty()) {
      return 0
    }

    purgeStaleUserData(staleUserIds)
    return staleUserIds.size
  }

  private fun findStaleUserIds(cutoffMillis: Long): List<UUID> = jdbcTemplate.query(
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
    ORDER BY ua.user_id
    LIMIT :batchSize
    """.trimIndent(),
    mapOf(
      "cutoffMillis" to cutoffMillis,
      "batchSize" to batchSize,
    ),
  ) { rs, _ ->
    UUID.fromString(rs.getString("user_id"))
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
    val revisionsToDelete = findRevisionIdsForDeletedAuditData(userIds, usernames)

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

    deleteOrphanedRevisionInfo(revisionsToDelete)
  }

  private fun findRevisionIdsForDeletedAuditData(userIds: Collection<UUID>, usernames: Collection<String>): List<Long> {
    val revisions = linkedSetOf<Long>()

    if (userIds.isNotEmpty()) {
      revisions += jdbcTemplate.queryForList(
        """
        SELECT DISTINCT rev FROM users_audit WHERE user_id IN (:userIds)
        UNION
        SELECT DISTINCT rev FROM user_account_audit WHERE user_id IN (:userIds)
        UNION
        SELECT DISTINCT rev FROM user_emails_audit WHERE user_id IN (:userIds)
        """.trimIndent(),
        mapOf("userIds" to userIds.toList()),
        Long::class.java,
      ).filterNotNull()
    }

    if (usernames.isNotEmpty()) {
      val usernameList = usernames.toList()
      revisions += jdbcTemplate.queryForList(
        """
        SELECT DISTINCT rev FROM user_roles_audit WHERE username IN (:usernames)
        UNION
        SELECT DISTINCT rev FROM user_accessible_caseloads_audit WHERE username IN (:usernames)
        UNION
        SELECT DISTINCT rev FROM user_caseload_administrators_audit WHERE username IN (:usernames)
        UNION
        SELECT DISTINCT rev FROM user_caseload_members_audit WHERE username IN (:usernames)
        """.trimIndent(),
        mapOf("usernames" to usernameList),
        Long::class.java,
      ).filterNotNull()
    }

    return revisions.toList()
  }

  private fun deleteOrphanedRevisionInfo(revisions: Collection<Long>) {
    revisions.chunked(batchSize).forEach { revisionBatch ->
      jdbcTemplate.update(
        """
        DELETE FROM revinfo r
        WHERE r.rev IN (:revisions)
        AND NOT EXISTS (SELECT 1 FROM users_audit ua WHERE ua.rev = r.rev)
        AND NOT EXISTS (SELECT 1 FROM user_account_audit uaa WHERE uaa.rev = r.rev)
        AND NOT EXISTS (SELECT 1 FROM user_emails_audit uea WHERE uea.rev = r.rev)
        AND NOT EXISTS (SELECT 1 FROM user_roles_audit ura WHERE ura.rev = r.rev)
        AND NOT EXISTS (SELECT 1 FROM user_accessible_caseloads_audit uaca WHERE uaca.rev = r.rev)
        AND NOT EXISTS (SELECT 1 FROM user_caseload_administrators_audit ucaa WHERE ucaa.rev = r.rev)
        AND NOT EXISTS (SELECT 1 FROM user_caseload_members_audit ucma WHERE ucma.rev = r.rev)
        """.trimIndent(),
        mapOf("revisions" to revisionBatch),
      )
    }
  }

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
