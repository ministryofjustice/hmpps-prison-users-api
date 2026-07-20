package uk.gov.justice.digital.hmpps.prisonusersapi.service

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.PrisonUserSyncRequest
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccessibleCaseload
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccessibleCaseloadId
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccount
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserEmail
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserRole
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserRoleId
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.CaseloadRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserAccountRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserRoleRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UsersRepository

@Service
class SyncService(
  private val usersRepository: UsersRepository,
  private val userAccountRepository: UserAccountRepository,
  private val caseloadRepository: CaseloadRepository,
  private val userRoleRepository: UserRoleRepository,
  private val primaryEmailDetector: PrimaryEmailDetector
) {

  @Transactional
  fun syncUser(legacyStaffId: Long, request: PrisonUserSyncRequest) {
    val user = usersRepository.findByLegacyStaffId(legacyStaffId)
      .orElseThrow(UserNotFoundException("User with legacy staff id $legacyStaffId not found"))

    // Update User scalar fields. Pass an empty userEmails list so JPA orphanRemoval
    // automatically deletes the old emails during the merge/flush.
    val updatedUser = usersRepository.saveAndFlush(
      user.copy(
        firstName = request.firstName,
        lastName = request.lastName,
        status = request.status,
        modifiedTimestamp = request.modifiedTimestamp,
        modifiedBy = request.modifiedBy,
        userEmails = mutableListOf(),
      ),
    )

    // Insert new emails directly into the managed collection so JPA handles the INSERT via cascade.
    val primaryEmail = primaryEmailDetector.getPrimaryEmail(request.emails)

    request.emails.forEach { syncEmail ->
      updatedUser.addUserEmail(
        UserEmail(
          email = requireNotNull(syncEmail.email),
          isPrimary = syncEmail.email == primaryEmail,
          createdBy = requireNotNull(syncEmail.createdBy),
          createdTimestamp = requireNotNull(syncEmail.createdTimestamp),
          modifiedBy = syncEmail.modifiedBy,
          modifiedTimestamp = syncEmail.modifiedTimestamp,
          user = updatedUser,
        ),
      )
    }

    // Load existing accounts for this user (with caseloads eagerly via withCaseloads graph).
    val existingAccounts = userAccountRepository.findAllByUserUserId(requireNotNull(user.userId))
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

      // Insert new roles via repository (no JPA cascade path).
      userRoleRepository.saveAll(
        syncAccount.roles.map { syncRole ->
          UserRole(
            id = UserRoleId(account.username, syncRole.roleCode),
            createdBy = syncRole.createdBy,
            createdTimestamp = syncRole.createdTimestamp,
          )
        },
      )

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
  }
}
