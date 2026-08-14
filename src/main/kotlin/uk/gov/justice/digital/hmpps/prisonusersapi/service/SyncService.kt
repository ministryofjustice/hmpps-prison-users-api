package uk.gov.justice.digital.hmpps.prisonusersapi.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.PrisonUserSyncRequest
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.SyncLock
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.User
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccessibleCaseload
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccessibleCaseloadId
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccount
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserEmail
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserRole
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserRoleId
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.CaseloadRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.SyncLockRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserAccountRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserRoleRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UsersRepository

@Service
class SyncService(
  private val usersRepository: UsersRepository,
  private val userAccountRepository: UserAccountRepository,
  private val caseloadRepository: CaseloadRepository,
  private val userRoleRepository: UserRoleRepository,
  private val primaryEmailDetector: PrimaryEmailDetector,
  private val syncLockRepository: SyncLockRepository,
  transactionManager: PlatformTransactionManager,
  @Value($$"${sync.lock.retry-backoff-ms:50}") private val lockRetryBackoffMs: Long,
  @Value($$"${sync.lock.max-wait-ms:5000}") private val lockMaxWaitMs: Long,
) {

  private val transactionTemplate = TransactionTemplate(transactionManager)

  fun syncUser(legacyStaffId: Long, request: PrisonUserSyncRequest) {
    val startedAtMs = System.currentTimeMillis()

    while (true) {
      try {
        transactionTemplate.executeWithoutResult {
          syncUserInTransaction(legacyStaffId, request)
        }
        return
      } catch (e: SyncLockBusyException) {
        if (System.currentTimeMillis() - startedAtMs >= lockMaxWaitMs) {
          throw SyncLockAcquisitionTimeoutException(
            "Timed out after ${lockMaxWaitMs}ms waiting to acquire sync lock for legacy staff id $legacyStaffId",
            e,
          )
        }
        pauseFor(legacyStaffId)
      }
    }
  }

  private fun pauseFor(legacyStaffId: Long) {
    try {
      Thread.sleep(lockRetryBackoffMs)
    } catch (interrupted: InterruptedException) {
      Thread.currentThread().interrupt()
      throw SyncLockAcquisitionTimeoutException(
        "Interrupted while waiting to acquire sync lock for legacy staff id $legacyStaffId",
        interrupted,
      )
    }
  }

  private fun syncUserInTransaction(legacyStaffId: Long, request: PrisonUserSyncRequest) {
    try {
      // Serialize sync operations by holding a lock row for the duration of this transaction.
      try {
        syncLockRepository.saveAndFlush(
          SyncLock(
            legacyStaffId = legacyStaffId,
            lockedAt = java.time.LocalDateTime.now(),
            lockedBy = Thread.currentThread().name,
          ),
        )
      } catch (e: DataIntegrityViolationException) {
        throw SyncLockBusyException("Sync lock is already held for legacy staff id $legacyStaffId", e)
      }

      val updatedUser = usersRepository.findByLegacyStaffId(legacyStaffId)
        .map {
          mergeRequestIntoUser(it, request)
        }
        .orElseGet {
          usersRepository.saveAndFlush(
            User(
              firstName = request.firstName,
              lastName = request.lastName,
              status = request.status,
              legacyStaffId = legacyStaffId,
              createdTimestamp = request.createdTimestamp,
              createdBy = request.createdBy,
              modifiedTimestamp = request.modifiedTimestamp,
              modifiedBy = request.modifiedBy,
              userEmails = mutableListOf(),
            ),
          )
        }

      // Insert new emails directly into the managed collection so JPA handles the INSERT via cascade.
      val primaryEmail = primaryEmailDetector.getPrimaryEmail(request.emails)

      request.emails.forEach { syncEmail ->
        updatedUser.addUserEmail(
          UserEmail(
            email = syncEmail.email,
            isPrimary = syncEmail.email == primaryEmail,
            createdBy = syncEmail.createdBy,
            createdTimestamp = syncEmail.createdTimestamp,
            modifiedBy = syncEmail.modifiedBy,
            modifiedTimestamp = syncEmail.modifiedTimestamp,
            user = updatedUser,
          ),
        )
      }

      // Load existing accounts for this user (with caseloads eagerly via withCaseloads graph).
      val existingAccounts = userAccountRepository.findAllByUserUserId(requireNotNull(updatedUser.userId))
      val requestAccountsByUsername = request.accounts.associateBy { it.username }

      // Remove accounts that are no longer present in the sync request.
      val accountsToRemove = existingAccounts.filter { it.username !in requestAccountsByUsername }
      if (accountsToRemove.isNotEmpty()) {
        // UserRole has no JPA cascade from UserAccount, so delete roles manually first.
        userRoleRepository.deleteAllByIdUsernameIn(accountsToRemove.map { it.username })
        // deleteAll cascades to userAccessibleCaseloads via orphanRemoval.
        userAccountRepository.deleteAll(accountsToRemove)
        userAccountRepository.flush()
      }

      // Validate and load all caseloads referenced by accounts in the request.
      val allRequestedCaseloadIds = request.accounts
        .flatMap { it.caseloads.map { c -> c.caseloadId } }
        .toSet()

      val caseloadsById = if (allRequestedCaseloadIds.isEmpty()) {
        emptyMap()
      } else {
        val found = caseloadRepository.findAllById(allRequestedCaseloadIds)
        val missingIds = allRequestedCaseloadIds - found.map { it.id }.toSet()
        if (missingIds.isNotEmpty()) throw CaseloadNotFoundException("Caseload(s) $missingIds not found")
        found.associateBy { it.id }
      }

      // Update or create each account from the request.
      request.accounts.forEach { syncAccount ->
        val activeCaseload = syncAccount.activeCaseloadId?.let { activeCaseloadId ->
          // Active caseload may or may not appear in the accessible caseloads list.
          caseloadsById[activeCaseloadId]
            ?: caseloadRepository.findByIdOrNull(activeCaseloadId)
            ?: throw CaseloadNotFoundException("Active caseload $activeCaseloadId not found for user ${syncAccount.username}")
        }

        val existingAccount = existingAccounts.find { it.username == syncAccount.username }

        if (existingAccount != null) {
          // Delete existing roles before saving (no JPA cascade from UserAccount → UserRole).
          userRoleRepository.deleteAllByIdUsernameIn(listOf(syncAccount.username))
        }

        // Save the account. Pass empty userAccessibleCaseloads so JPA orphanRemoval
        // automatically deletes the old caseloads during the merge/flush.
        val account = if (existingAccount != null) {
          userAccountRepository.saveAndFlush(
            existingAccount.copy(
              accountType = syncAccount.accountType,
              accountStatus = syncAccount.accountStatus,
              activeCaseload = activeCaseload,
              lastLoggedIn = syncAccount.lastLoggedIn,
              modifiedTimestamp = syncAccount.modifiedTimestamp,
              modifiedBy = syncAccount.modifiedBy,
              userAccessibleCaseloads = mutableListOf(),
            ),
          )
        } else {
          userAccountRepository.saveAndFlush(
            UserAccount(
              username = syncAccount.username,
              user = updatedUser,
              accountType = syncAccount.accountType,
              accountStatus = syncAccount.accountStatus,
              activeCaseload = activeCaseload,
              lastLoggedIn = syncAccount.lastLoggedIn,
              createdBy = syncAccount.createdBy,
              createdTimestamp = syncAccount.createdTimestamp,
              modifiedBy = syncAccount.modifiedBy,
              modifiedTimestamp = syncAccount.modifiedTimestamp,
            ),
          )
        }

        // Insert new roles into the managed collection so JPA handles the INSERT via cascade.
        syncAccount.roles.forEach { syncRole ->
          account.userRoleCodes.add(
            UserRole(
              id = UserRoleId(account.username, syncRole.roleCode),
              userAccount = account,
              createdBy = syncRole.createdBy,
              createdTimestamp = syncRole.createdTimestamp,
            ),
          )
        }

        // Insert new accessible caseloads into the managed collection so JPA handles the INSERT via cascade.
        syncAccount.caseloads.forEach { syncCaseload ->
          val caseload = requireNotNull(caseloadsById[syncCaseload.caseloadId])
          account.userAccessibleCaseloads.add(
            UserAccessibleCaseload(
              id = UserAccessibleCaseloadId(account.username, caseload.id),
              caseload = caseload,
              userAccount = account,
              createdBy = syncCaseload.createdBy,
              createdTimestamp = syncCaseload.createdTimestamp,
            ),
          )
        }
      }
    } finally {
      // Release the lock by deleting the row before transaction completes.
      syncLockRepository.deleteById(legacyStaffId)
    }
  }

  private fun mergeRequestIntoUser(user: User, request: PrisonUserSyncRequest): User = usersRepository.saveAndFlush(
    user.copy(
      firstName = request.firstName,
      lastName = request.lastName,
      status = request.status,
      modifiedTimestamp = request.modifiedTimestamp,
      modifiedBy = request.modifiedBy,
      userEmails = mutableListOf(),
    ),
  )
}

class SyncLockAcquisitionTimeoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private class SyncLockBusyException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
