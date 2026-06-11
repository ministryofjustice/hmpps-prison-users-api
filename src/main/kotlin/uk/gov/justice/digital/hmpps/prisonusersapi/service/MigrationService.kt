package uk.gov.justice.digital.hmpps.prisonusersapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserMigrationRequest
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserMigrationResponse
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.Caseload
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccessibleCaseload
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccessibleCaseloadId
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccount
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserRole
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserRoleId
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.CaseloadRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserAccessibleCaseloadRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserAccountRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserRoleRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UsersRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.service.converters.toUser
import uk.gov.justice.digital.hmpps.prisonusersapi.service.converters.toUserAccounts

@Service
class MigrationService(
  private val usersRepository: UsersRepository,
  private val userAccountRepository: UserAccountRepository,
  private val caseloadRepository: CaseloadRepository,
  private val userAccessibleCaseloadRepository: UserAccessibleCaseloadRepository,
  private val userRoleRepository: UserRoleRepository,
) {

  @Transactional
  fun migrateUser(userMigrationRequest: UserMigrationRequest): UserMigrationResponse {
    // Create user
    if (usersRepository.existsUsersByLegacyStaffId(userMigrationRequest.user.id)) {
      throw UserAlreadyExistsException("User with legacy staff id ${userMigrationRequest.user.id} already exists")
    }

    val user = usersRepository.saveAndFlush(userMigrationRequest.toUser())

    // Load all caseloads by id and group migrated accessible caseloads by username
    val allCaseloadsById = loadAllUserAccessibleCaseloadsMappedByCaseloadId(userMigrationRequest)
    val migratedAccessibleCaseloadsByUsername = userMigrationRequest.accessibleCaseloads?.groupBy { it.username }

    // Define mapper from activeCaseloadId to Caseload
    val toActiveCaseloadMapper: (activeCaseloadId: String?, username: String) -> Caseload? = { activeCaseloadId, username ->

      // Get accessible caseloads for the user
      val userAccessibleCaseloads = migratedAccessibleCaseloadsByUsername?.get(username)

      // Find the active caseload
      var migratedUserAccessibleCaseload = userAccessibleCaseloads?.find { it.caseloadId == activeCaseloadId }?.let {
        allCaseloadsById?.get(it.caseloadId)
      }

      if (migratedUserAccessibleCaseload == null) {
        // If the active caseload is not found, set it to the first caseload in the list of user-accessible caseloads
        migratedUserAccessibleCaseload = allCaseloadsById?.get(userAccessibleCaseloads?.get(0)?.caseloadId)
      }

      migratedUserAccessibleCaseload
    }

    // 2. Create user accounts
    val userAccounts = userAccountRepository.saveAllAndFlush<UserAccount>(userMigrationRequest.toUserAccounts(user, toActiveCaseloadMapper))

    // 3. Create user roles
    userMigrationRequest.roles?.let {
      val userRoles = mutableListOf<UserRole>()
      // Group migrated user roles by username
      val migratedRolesByUsername = userMigrationRequest.roles.groupBy { it.username }
      migratedRolesByUsername.entries.forEach { migratedRolesForUser ->
        val userAccount = userAccounts.find { it.username == migratedRolesForUser.key }
        if (userAccount == null) {
          throw UserRoleWithoutUserAccountException("User account for username ${migratedRolesForUser.key} not found during user role migration")
        }

        migratedRolesForUser.value.forEach { migratedUserRole ->
          UserRoleId(userAccount.username, migratedUserRole.roleCode).let {
            userRoles.add(UserRole(it, migratedUserRole.createdBy, migratedUserRole.createdTimestamp))
          }
        }
      }

      userRoleRepository.saveAll(userRoles)
    }

    // 4. Create user caseloads

    userMigrationRequest.accessibleCaseloads?.let {
      val userAccessibleCaseloads = mutableListOf<UserAccessibleCaseload>()

      migratedAccessibleCaseloadsByUsername?.entries?.forEach { migratedAccessibleCaseloadsForUser ->
        val userAccount = userAccounts.find { it.username == migratedAccessibleCaseloadsForUser.key }
        if (userAccount == null) {
          throw UserAccessibleCaseloadsWithoutUserAccountException("User account for username ${migratedAccessibleCaseloadsForUser.key} not found during user accessible caseload migration")
        }

        migratedAccessibleCaseloadsByUsername[userAccount.username]?.forEach { migratedUserAccessibleCaseload ->
          val caseload = allCaseloadsById?.get(migratedUserAccessibleCaseload.caseloadId)
            ?: throw CaseloadNotFoundException("Caseload ${migratedUserAccessibleCaseload.caseloadId} not found")

          UserAccessibleCaseloadId(userAccount.username, caseload.id).let {
            userAccessibleCaseloads.add(UserAccessibleCaseload(it, caseload, userAccount, migratedUserAccessibleCaseload.createdBy, migratedUserAccessibleCaseload.createdTimestamp))
          }
        }
      }
      userAccessibleCaseloadRepository.saveAll(userAccessibleCaseloads)
    }

    return UserMigrationResponse(user.userId.toString(), user.legacyStaffId)
  }

  private fun loadAllUserAccessibleCaseloadsMappedByCaseloadId(userMigrationRequest: UserMigrationRequest): Map<String, Caseload>? {
    val allCaseloadIds = userMigrationRequest.accessibleCaseloads?.map { it.caseloadId }?.toSet()
    return allCaseloadIds?.let { caseloadRepository.findAllById(allCaseloadIds).associateBy { it.id } }
  }
}

class UserRoleWithoutUserAccountException(message: String?) : RuntimeException(message)

class CaseloadNotFoundException(message: String?) : RuntimeException(message)

class UserAccessibleCaseloadsWithoutUserAccountException(message: String?) : RuntimeException(message)

class UserAlreadyExistsException(message: String?) : RuntimeException(message)
