package uk.gov.justice.digital.hmpps.prisonusersapi.service

import org.springframework.data.repository.findByIdOrNull
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
    if (usersRepository.existsUsersByLegacyStaffId(userMigrationRequest.user?.staffId!!)) {
      throw UserAlreadyExistsException("User with legacy staff id ${userMigrationRequest.user!!.staffId} already exists")
    }

    val user = usersRepository.saveAndFlush(userMigrationRequest.toUser())

    val allCaseloadsById = loadAllUserAccessibleCaseloadsMappedByCaseloadId(userMigrationRequest)
    val migratedAccessibleCaseloadsByUsername = userMigrationRequest.accessibleCaseloads?.groupBy { it.username }

    val toActiveCaseloadMapper: (activeCaseloadId: String?, username: String) -> Caseload? =
      toActiveCaseloadMapper@{ activeCaseloadId, username ->

        if (activeCaseloadId == null) return@toActiveCaseloadMapper null
        if (caseloadRepository.findByIdOrNull(activeCaseloadId) == null) throw CaseloadNotFoundException("Active caseload $activeCaseloadId not found for user $username")

        val activeCaseload: Caseload = allCaseloadsById?.get(activeCaseloadId)
          ?: throw ActiveCaseloadNotInUserAccessibleCaseloadsException("Active caseload $activeCaseloadId not found for user $username")

        val migratedUserAccessibleCaseloadsForUsername = migratedAccessibleCaseloadsByUsername?.get(username)
        if (migratedUserAccessibleCaseloadsForUsername.isNullOrEmpty()) {
          throw ActiveCaseloadNotInUserAccessibleCaseloadsException("Active caseload $activeCaseloadId not found in user accessible caseloads for user $username")
        }

        migratedUserAccessibleCaseloadsForUsername.let { migratedUserAccessibleCaseloads ->
          val migratedUserAccessibleCaseload = migratedUserAccessibleCaseloads.find { it.caseloadId == activeCaseloadId }
          if (migratedUserAccessibleCaseload == null) throw ActiveCaseloadNotInUserAccessibleCaseloadsException("Active caseload $activeCaseloadId not found in user accessible caseloads for user $username")
        }

        activeCaseload
      }

    userMigrationRequest.accounts?.let {
      val userAccounts = userAccountRepository.saveAllAndFlush<UserAccount>(
        userMigrationRequest.toUserAccounts(
          user,
          toActiveCaseloadMapper,
        )!!,
      )

      userMigrationRequest.roles?.let {
        val userRoles = mutableListOf<UserRole>()
        val migratedRolesByUsername = userMigrationRequest.roles.groupBy { it.username }
        migratedRolesByUsername.entries.forEach { migratedRolesForUser ->
          val userAccount = userAccounts.find { it.username == migratedRolesForUser.key }
          if (userAccount == null) {
            throw UserRoleWithoutUserAccountException("User account for username ${migratedRolesForUser.key} not found during user role migration")
          }

          migratedRolesForUser.value.forEach { migratedUserRole ->
            UserRoleId(userAccount.username, migratedUserRole.roleCode!!).let {
              userRoles.add(
                UserRole(
                  it,
                  migratedUserRole.createdBy!!,
                  migratedUserRole.createdTimestamp!!,
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
                  createdBy = migratedUserAccessibleCaseload.createdBy!!,
                  createdTimestamp = migratedUserAccessibleCaseload.createdTimestamp!!,
                ),
              )
            }
          }
        }
        userAccessibleCaseloadRepository.saveAll(userAccessibleCaseloads)
      }
    }
    return UserMigrationResponse(user.userId.toString(), user.legacyStaffId)
  }

  private fun loadAllUserAccessibleCaseloadsMappedByCaseloadId(userMigrationRequest: UserMigrationRequest): Map<String, Caseload>? {
    var userAccessibleCaseloadsByIdMap: Map<String, Caseload>? = null
    val allCaseloadIds = userMigrationRequest.accessibleCaseloads?.map { it.caseloadId!! }?.toSet()
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

class UserAlreadyExistsException(message: String?) : RuntimeException(message)
