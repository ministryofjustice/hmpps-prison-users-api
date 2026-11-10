package uk.gov.justice.digital.hmpps.prisonusersapi.service

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.PrisonUserSyncRequest
import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.PrisonUserSyncResponse
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.User
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccessibleCaseload
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccessibleCaseloadId
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccount
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserEmail
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserRole
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserRoleId
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.CaseloadRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserAccountRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserCaseloadAdministratorRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserCaseloadMemberRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UsersRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.service.converters.toUser
import uk.gov.justice.digital.hmpps.prisonusersapi.service.converters.toUserCaseloadAdministrator
import uk.gov.justice.digital.hmpps.prisonusersapi.service.converters.toUserCaseloadMember

@Transactional
@Service
class SyncService(
  private val usersRepository: UsersRepository,
  private val userAccountRepository: UserAccountRepository,
  private val caseloadRepository: CaseloadRepository,
  private val userCaseloadAdministratorRepository: UserCaseloadAdministratorRepository,
  private val userCaseloadMemberRepository: UserCaseloadMemberRepository,
  private val primaryEmailDetector: PrimaryEmailDetector,
  private val syncLockTemplate: SyncLockTemplate,
) {

  fun syncUser(legacyStaffId: Long, request: PrisonUserSyncRequest): PrisonUserSyncResponse = syncLockTemplate.execute(legacyStaffId) { processSyncUser(legacyStaffId, request) }

  fun deleteUser(legacyStaffId: Long) {
    syncLockTemplate.execute(legacyStaffId) { processDeleteUser(legacyStaffId) }
  }

  private fun processSyncUser(legacyStaffId: Long, request: PrisonUserSyncRequest): PrisonUserSyncResponse {
    val updatedUser = usersRepository.findByLegacyStaffId(legacyStaffId)
      .map {
        mergeRequestIntoUser(it, request)
      }
      .orElseGet {
        usersRepository.saveAndFlush(
          request.toUser(legacyStaffId),
        )
      }

    // Sync emails: delete absent, update isPrimary, create new
    val requestEmailsByAddress = request.emails.associateBy { it.email }
    val primaryEmailAddress = primaryEmailDetector.getPrimaryEmail(request.emails)
    val existingEmailsByAddress = updatedUser.userEmails.associateBy { it.email }

    // Delete emails absent from the request
    val emailsToDelete = updatedUser.userEmails.filter { it.email !in requestEmailsByAddress }
    emailsToDelete.forEach { updatedUser.userEmails.remove(it) }

    // Update isPrimary flag for existing emails and create new emails from request
    requestEmailsByAddress.forEach { (emailAddress, syncEmail) ->
      val existing = existingEmailsByAddress[emailAddress]
      if (existing != null) {
        // Email exists; update isPrimary if needed by recreating it
        val shouldBePrimary = emailAddress == primaryEmailAddress
        if (existing.isPrimary != shouldBePrimary) {
          updatedUser.userEmails.remove(existing)
          updatedUser.addUserEmail(
            UserEmail(
              id = existing.id,
              email = emailAddress,
              isPrimary = shouldBePrimary,
              createdBy = existing.createdBy,
              createdTimestamp = existing.createdTimestamp,
              modifiedBy = syncEmail.modifiedBy,
              modifiedTimestamp = syncEmail.modifiedTimestamp,
              user = updatedUser,
            ),
          )
        }
      } else {
        // New email; create with correct isPrimary flag
        updatedUser.addUserEmail(
          UserEmail(
            email = emailAddress,
            isPrimary = emailAddress == primaryEmailAddress,
            createdBy = syncEmail.createdBy,
            createdTimestamp = syncEmail.createdTimestamp,
            modifiedBy = syncEmail.modifiedBy,
            modifiedTimestamp = syncEmail.modifiedTimestamp,
            user = updatedUser,
          ),
        )
      }
    }
    usersRepository.flush()

    // Load existing accounts for this user (with caseloads eagerly via withCaseloads graph).
    val existingAccounts = userAccountRepository.findAllByUserUserId(requireNotNull(updatedUser.userId))
    val requestAccountsByUsername = request.accounts.associateBy { it.username }
    val requestAccountUsernames = requestAccountsByUsername.keys

    // Remove accounts that are no longer present in the sync request.
    val accountsToRemove = existingAccounts.filter { it.username !in requestAccountsByUsername }
    if (accountsToRemove.isNotEmpty()) {
      userAccountRepository.deleteAll(accountsToRemove)
      userAccountRepository.flush()
    }

    // Validate and load all caseloads referenced by accounts in the request.
    val allRequestedCaseloadIds = request.accounts
      .flatMap { it.caseloads.map { c -> c.caseloadId } }
      .plus(request.accounts.flatMap { it.administrationCaseloads.map { c -> c.caseloadId } })
      .plus(request.accounts.flatMap { it.memberCaseloads.map { c -> c.caseloadId } })
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
    val syncedAccountsByUsername = mutableMapOf<String, UserAccount>()
    request.accounts.forEach { syncAccount ->
      val activeCaseload = syncAccount.activeCaseloadId?.let { activeCaseloadId ->
        if (!caseloadsById.containsKey(activeCaseloadId)) {
          if (caseloadRepository.findByIdOrNull(activeCaseloadId) == null) throw CaseloadNotFoundException("Active caseload $activeCaseloadId not found for user ${syncAccount.username}")
        }

        val accountCaseloads = syncAccount.caseloads.map { caseloadsById[it.caseloadId] }.associateBy { it!!.id }
        accountCaseloads[activeCaseloadId]
          ?: throw ActiveCaseloadNotInUserAccessibleCaseloadsException("Active caseload $activeCaseloadId not found in user accessible caseloads for user ${syncAccount.username}")
      }

      val existingAccount = existingAccounts.find { it.username == syncAccount.username }

      // Save the account with updated scalar fields
      val account = if (existingAccount != null) {
        userAccountRepository.saveAndFlush(
          existingAccount.copy(
            accountType = syncAccount.accountType,
            accountStatus = syncAccount.accountStatus,
            activeCaseload = activeCaseload,
            lastLoggedIn = syncAccount.lastLoggedIn,
            modifiedTimestamp = syncAccount.modifiedTimestamp,
            modifiedBy = syncAccount.modifiedBy,
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

      // Sync roles: delete absent, create new
      val requestRolesByCode = syncAccount.roles.associateBy { it.roleCode }
      val existingRolesByCode = account.userRoleCodes.associateBy { it.id.roleCode }

      // Delete roles absent from the request
      val rolesToDelete = account.userRoleCodes.filter { it.id.roleCode !in requestRolesByCode }
      rolesToDelete.forEach { account.userRoleCodes.remove(it) }

      // Create new roles from request
      requestRolesByCode.forEach { (roleCode, syncRole) ->
        if (roleCode !in existingRolesByCode) {
          account.userRoleCodes.add(
            UserRole(
              id = UserRoleId(account.username, syncRole.roleCode),
              userAccount = account,
              createdBy = syncRole.createdBy,
              createdTimestamp = syncRole.createdTimestamp,
            ),
          )
        }
      }

      // Sync accessible caseloads: delete absent, create new
      val requestCaseloadsByCode = syncAccount.caseloads.associateBy { it.caseloadId }
      val existingCaseloadsByCode = account.userAccessibleCaseloads.associateBy { it.id.caseloadId }

      // Delete caseloads absent from the request
      val caseloadsToDelete = account.userAccessibleCaseloads.filter { it.id.caseloadId !in requestCaseloadsByCode }
      caseloadsToDelete.forEach { account.userAccessibleCaseloads.remove(it) }

      // Create new caseloads from request
      requestCaseloadsByCode.forEach { (caseloadId, syncCaseload) ->
        if (caseloadId !in existingCaseloadsByCode) {
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

      syncedAccountsByUsername[account.username] = account
    }

    // Flush all account and role/caseload changes before syncing account-level caseload administrator/member links
    userAccountRepository.flush()
    if (requestAccountUsernames.isNotEmpty()) {
      // Process all administration and member caseloads for all accounts
      val allAdministrationCaseloads = request.accounts.flatMap { account ->
        account.administrationCaseloads.map { adminCaseload -> account.username to adminCaseload }
      }
      val allMemberCaseloads = request.accounts.flatMap { account ->
        account.memberCaseloads.map { memberCaseload -> account.username to memberCaseload }
      }

      // Sync administration caseloads: update existing, create new, delete absent
      val existingAdministrators = userCaseloadAdministratorRepository.findAllByIdUsernameIn(requestAccountUsernames)
      val requestAdministratorsByKey = allAdministrationCaseloads.associateBy { (username, caseload) -> username to caseload.caseloadId }
      val existingAdministratorsByKey = existingAdministrators.associateBy { it.id.username to it.id.caseloadId }

      // Delete administrators absent from the request
      val administratorsToDelete = existingAdministrators.filter { (it.id.username to it.id.caseloadId) !in requestAdministratorsByKey }
      if (administratorsToDelete.isNotEmpty()) {
        userCaseloadAdministratorRepository.deleteAll(administratorsToDelete)
        userCaseloadAdministratorRepository.flush()
      }

      // Update or create administrators present in the request
      userCaseloadAdministratorRepository.saveAll(
        allAdministrationCaseloads.map { (username, syncAdministratorCaseload) ->
          val key = username to syncAdministratorCaseload.caseloadId
          val existing = existingAdministratorsByKey[key]
          existing // Update existing record with any changed fields
            ?.copy(
              active = syncAdministratorCaseload.active,
              expiryDate = syncAdministratorCaseload.expiryDate,
              modifiedBy = syncAdministratorCaseload.modifiedBy,
              modifiedTimestamp = syncAdministratorCaseload.modifiedTimestamp,
            )
            ?: // Create new record
            syncAdministratorCaseload.toUserCaseloadAdministrator(
              userAccount = requireNotNull(syncedAccountsByUsername[username]),
              caseload = requireNotNull(caseloadsById[syncAdministratorCaseload.caseloadId]),
            )
        },
      )
      userCaseloadAdministratorRepository.flush()

      // Sync member caseloads: update existing, create new, delete absent
      val existingMembers = userCaseloadMemberRepository.findAllByIdUsernameIn(requestAccountUsernames)
      val requestMembersByKey = allMemberCaseloads.associateBy { (username, caseload) -> username to caseload.caseloadId }
      val existingMembersByKey = existingMembers.associateBy { it.id.username to it.id.caseloadId }

      // Delete members absent from the request
      val membersToDelete = existingMembers.filter { (it.id.username to it.id.caseloadId) !in requestMembersByKey }
      if (membersToDelete.isNotEmpty()) {
        userCaseloadMemberRepository.deleteAll(membersToDelete)
        userCaseloadMemberRepository.flush()
      }

      // Update or create members present in the request
      userCaseloadMemberRepository.saveAll(
        allMemberCaseloads.map { (username, syncMemberCaseload) ->
          val key = username to syncMemberCaseload.caseloadId
          val existing = existingMembersByKey[key]
          existing // Update existing record with any changed fields
            ?.copy(
              startDate = syncMemberCaseload.startDate,
              expiryDate = syncMemberCaseload.expiryDate,
              active = syncMemberCaseload.active,
              modifiedBy = syncMemberCaseload.modifiedBy,
              modifiedTimestamp = syncMemberCaseload.modifiedTimestamp,
            )
            ?: // Create new record
            syncMemberCaseload.toUserCaseloadMember(
              userAccount = requireNotNull(syncedAccountsByUsername[username]),
              caseload = requireNotNull(caseloadsById[syncMemberCaseload.caseloadId]),
            )
        },
      )
      userCaseloadMemberRepository.flush()
    }

    return PrisonUserSyncResponse(updatedUser.userId.toString(), updatedUser.legacyStaffId)
  }

  private fun processDeleteUser(legacyStaffId: Long) {
    val user = usersRepository.findByLegacyStaffId(legacyStaffId)
      .orElseThrow { UserNotFoundException("User with legacy staff id $legacyStaffId not found") }

    usersRepository.delete(user)
    usersRepository.flush()
  }

  private fun mergeRequestIntoUser(user: User, request: PrisonUserSyncRequest): User = usersRepository.saveAndFlush(
    user.copy(
      firstName = request.firstName,
      lastName = request.lastName,
      status = request.status,
      modifiedTimestamp = request.modifiedTimestamp,
      modifiedBy = request.modifiedBy,
    ),
  )
}

class ActiveCaseloadNotInUserAccessibleCaseloadsException(message: String?) : RuntimeException(message)

class CaseloadNotFoundException(message: String?) : RuntimeException(message)
