package uk.gov.justice.digital.hmpps.prisonusersapi.service

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonusersapi.data.migrate.UserMigrationRequest
import uk.gov.justice.digital.hmpps.prisonusersapi.data.migrate.UserMigrationResponse
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.Caseload
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccessibleCaseload
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccessibleCaseloadId
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccount
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserEmail
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
  private val primaryEmailDetector: PrimaryEmailDetector,
) {

  @Transactional
  fun migrateUser(userMigrationRequest: UserMigrationRequest): UserMigrationResponse {
    val user = usersRepository.findByLegacyStaffId(userMigrationRequest.user.staffId)
      .map { existingUser ->
        usersRepository.saveAndFlush(
          existingUser.copy(
            firstName = userMigrationRequest.user.firstName,
            lastName = userMigrationRequest.user.lastName,
            status = userMigrationRequest.user.status,
            createdTimestamp = userMigrationRequest.user.createdTimestamp,
            createdBy = userMigrationRequest.user.createdBy,
            modifiedTimestamp = userMigrationRequest.user.modifiedTimestamp,
            modifiedBy = userMigrationRequest.user.modifiedBy,
            userEmails = mutableListOf(),
          ),
        ).also { updatedUser ->
          val emails = userMigrationRequest.user.emails.orEmpty().sortedBy { it.legacyEmailId }
          val primaryEmail = primaryEmailDetector.getPrimaryEmail(emails)

          emails.forEach { migratedEmail ->
            updatedUser.addUserEmail(
              UserEmail(
                email = migratedEmail.email,
                isPrimary = migratedEmail.email == primaryEmail,
                createdBy = migratedEmail.createdBy,
                createdTimestamp = migratedEmail.createdTimestamp,
                modifiedBy = migratedEmail.modifiedBy,
                modifiedTimestamp = migratedEmail.modifiedTimestamp,
                user = updatedUser,
              ),
            )
          }

          usersRepository.saveAndFlush(updatedUser)
        }
      }
      .orElseGet {
        usersRepository.saveAndFlush(userMigrationRequest.toUser(primaryEmailDetector))
      }

    val existingUserAccounts = user.userId?.let { userAccountRepository.findAllByUserUserId(it) }.orEmpty()
    if (existingUserAccounts.isNotEmpty()) {
      userRoleRepository.deleteAllByIdUsernameIn(existingUserAccounts.map { it.username })
      userAccountRepository.deleteAll(existingUserAccounts)
      userAccountRepository.flush()
    }

    val allCaseloadsById = loadAllUserAccessibleCaseloadsMappedByCaseloadId(userMigrationRequest)
    val migratedAccessibleCaseloadsByUsername = userMigrationRequest.accessibleCaseloads?.groupBy { it.username }

    val toActiveCaseloadMapper: (activeCaseloadId: String?, username: String) -> Caseload? =
      toActiveCaseloadMapper@{ activeCaseloadId, username ->

        if (activeCaseloadId == null) return@toActiveCaseloadMapper null
        if (caseloadRepository.findByIdOrNull(activeCaseloadId) == null) throw CaseloadNotFoundException("Active caseload $activeCaseloadId not found for user $username")

        val activeCaseload: Caseload? = allCaseloadsById?.get(activeCaseloadId)

        val migratedUserAccessibleCaseloadsForUsername = migratedAccessibleCaseloadsByUsername?.get(username)
        if (activeCaseload == null || migratedUserAccessibleCaseloadsForUsername.isNullOrEmpty()) {
          throw ActiveCaseloadNotInUserAccessibleCaseloadsException("Active caseload $activeCaseloadId not found in user accessible caseloads for user $username")
        }

        migratedUserAccessibleCaseloadsForUsername.let { migratedUserAccessibleCaseloads ->
          val migratedUserAccessibleCaseload = migratedUserAccessibleCaseloads.find { it.caseloadId == activeCaseloadId }
          if (migratedUserAccessibleCaseload == null) throw ActiveCaseloadNotInUserAccessibleCaseloadsException("Active caseload $activeCaseloadId not found in user accessible caseloads for user $username")
        }

        activeCaseload
      }

    val userAccounts = userMigrationRequest.accounts.orEmpty().let {
      if (it.isEmpty()) {
        emptyList()
      } else {
        userMigrationRequest.toUserAccounts(user, toActiveCaseloadMapper).orEmpty()
      }
    }

    if (userAccounts.isNotEmpty()) {
      userAccountRepository.saveAllAndFlush<UserAccount>(userAccounts)
    }

    userMigrationRequest.roles?.let {
      val userRoles = mutableListOf<UserRole>()
      val migratedRolesByUsername = userMigrationRequest.roles.groupBy { it.username }
      migratedRolesByUsername.entries.forEach { migratedRolesForUser ->
        val userAccount = userAccounts.find { it.username == migratedRolesForUser.key }
        if (userAccount == null) {
          throw UserRoleWithoutUserAccountException("User account for username ${migratedRolesForUser.key} not found during user role migration")
        }

        migratedRolesForUser.value.forEach { migratedUserRole ->
          UserRoleId(userAccount.username, migratedUserRole.roleCode).let {
            userRoles.add(
              UserRole(
                it,
                userAccount,
                migratedUserRole.createdBy,
                migratedUserRole.createdTimestamp,
              ),
            )
          }
        }
      }

      userRoleRepository.saveAll(userRoles)
    }

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
            userAccessibleCaseloads.add(
              UserAccessibleCaseload(
                it,
                caseload = caseload,
                userAccount = userAccount,
                createdBy = migratedUserAccessibleCaseload.createdBy,
                createdTimestamp = migratedUserAccessibleCaseload.createdTimestamp,
              ),
            )
          }
        }
      }
      userAccessibleCaseloadRepository.saveAll(userAccessibleCaseloads)
    }
    return UserMigrationResponse(user.userId.toString(), user.legacyStaffId)
  }

  private fun loadAllUserAccessibleCaseloadsMappedByCaseloadId(userMigrationRequest: UserMigrationRequest): Map<String, Caseload>? {
    var userAccessibleCaseloadsByIdMap: Map<String, Caseload>? = null
    val allCaseloadIds = userMigrationRequest.accessibleCaseloads?.map { it.caseloadId }?.toSet()
    allCaseloadIds?.let {
      val caseloadsById = caseloadRepository.findAllById(allCaseloadIds)
      if (caseloadsById.size != allCaseloadIds.size) {
        throw CaseloadNotFoundException(
          "Caseload(s) ${allCaseloadIds.minus(
            caseloadsById.map { it.id }.toSet(),
          )} not found",
        )
      }

      userAccessibleCaseloadsByIdMap = allCaseloadIds.let { caseloadsById.associateBy { it.id } }
    }

    return userAccessibleCaseloadsByIdMap
  }
}

class UserRoleWithoutUserAccountException(message: String?) : RuntimeException(message)

class ActiveCaseloadNotInUserAccessibleCaseloadsException(message: String?) : RuntimeException(message)

class CaseloadNotFoundException(message: String?) : RuntimeException(message)

class UserAccessibleCaseloadsWithoutUserAccountException(message: String?) : RuntimeException(message)

class UserAccountAlreadyExistsException(message: String?) : RuntimeException(message)

class UserAlreadyExistsException(message: String?) : RuntimeException(message)
