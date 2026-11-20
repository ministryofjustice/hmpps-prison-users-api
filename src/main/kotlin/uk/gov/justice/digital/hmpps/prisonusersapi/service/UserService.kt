package uk.gov.justice.digital.hmpps.prisonusersapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonusersapi.data.AccountStatus
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UsageType
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserAccountBasicDetails
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserAccountCaseloadDetail
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserAccountDetails
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserAdministeredBy
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserDetails
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserStatus
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccount
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserAccountRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserCaseloadMemberRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UsersRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.service.converters.toUserAccountBasicDetails
import uk.gov.justice.digital.hmpps.prisonusersapi.service.converters.toUserAccountCaseloadDetail
import uk.gov.justice.digital.hmpps.prisonusersapi.service.converters.toUserAccountDetails
import uk.gov.justice.digital.hmpps.prisonusersapi.service.converters.toUserDetails
import java.util.function.Supplier

@Service
@Transactional
class UserService(
  private val usersRepository: UsersRepository,
  private val userAccountRepository: UserAccountRepository,
  private val syncLockTemplate: SyncLockTemplate,
  private val userCaseloadMemberRepository: UserCaseloadMemberRepository,
) {

  @Transactional(readOnly = true)
  fun getCaseloads(username: String): UserAccountCaseloadDetail = userAccountRepository.findById(username).orElseThrow(UserNotFoundException("User $username not found"))
    .toUserAccountCaseloadDetail(removeDpsCaseload = true)

  @Transactional(readOnly = true)
  fun findUserBasicDetails(username: String) = userAccountRepository.findWithUserAndActiveCaseloadByUsername(username).orElseThrow(UserNotFoundException("User not found: $username not found")).toUserAccountBasicDetails()

  @Transactional(readOnly = true)
  fun findUserBasicDetails(usernames: List<String>): Map<String, UserAccountBasicDetails> {
    if (usernames.isEmpty()) return emptyMap()
    log.info("Fetching user basic details for {} usernames", usernames.size)
    val userDetails = userAccountRepository.findWithUserAndActiveCaseloadByUsernameIn(usernames)
      .map { it.toUserAccountBasicDetails() }
      .associateBy { it.username }
    log.info("Returning {} user basic details for {} usernames", userDetails.size, usernames.size)
    return userDetails
  }

  @Transactional(readOnly = true)
  fun findByUsername(username: String): UserAccountDetails {
    val userAccount = userAccountRepository.findWithUserAndActiveCaseloadAndUserRoleCodesByUsername(username)
      .orElseThrow(UserNotFoundException("User $username not found"))
    return userAccount.toUserAccountDetails()
  }

  fun unlockUser(username: String): UserAccount {
    val legacyStaffId = userAccountRepository.findLegacyStaffIdByUsername(username)
      .orElseThrow(UserNotFoundException("User $username not found"))

    return syncLockTemplate.execute(legacyStaffId) {
      val userAccount = userAccountRepository.findById(username)
        .orElseThrow(UserNotFoundException("User $username not found"))
      unlockUserAccount(userAccount)
    }
  }

  @Transactional(readOnly = true)
  fun isAdministeredBy(username: String, localAdministratorUsername: String) = UserAdministeredBy(
    username = username,
    localAdministratorUsername = localAdministratorUsername,
    administered = userCaseloadMemberRepository.isAdministeredBy(username, localAdministratorUsername),
  )

  fun updateFirstAndLastName(username: String, firstName: String, lastName: String): Pair<UserDetails, UserAccount> {
    val legacyStaffId = userAccountRepository.findLegacyStaffIdByUsername(username)
      .orElseThrow(UserNotFoundException("User $username not found"))
    return syncLockTemplate.execute(legacyStaffId) {
      val userAccount = userAccountRepository.findById(username)
        .filter { it.user.legacyStaffId == legacyStaffId }
        .orElseThrow(UserNotFoundException("User $username not found"))
      Pair(updateUserName(userAccount, firstName, lastName), userAccount)
    }
  }

  fun updateUserName(
    userAccount: UserAccount,
    firstName: String,
    lastName: String,
  ): UserDetails {
    userAccount.user.firstName = firstName
    userAccount.user.lastName = lastName
    usersRepository.save(userAccount.user)
    val userAccounts = userAccountRepository.findAllByUserUserId(userAccount.user.userId!!)
    val generalAccount = userAccounts.firstOrNull { it.isActive() && it.accountType == UsageType.GENERAL }
    val adminAccount = userAccounts.firstOrNull { it.isActive() && it.accountType == UsageType.ADMIN }
    return userAccount.user.toUserDetails(generalAccount, adminAccount)
  }

  private fun unlockUserAccount(userAccount: UserAccount): UserAccount {
    userAccount.user.status = UserStatus.ACTIVE
    userAccount.accountStatus = AccountStatus.OPEN
    return userAccountRepository.save(userAccount)
  }

  companion object {
    private val log = LoggerFactory.getLogger(UserService::class.java)
  }
}

class UserNotFoundException(message: String?) :
  RuntimeException(message),
  Supplier<UserNotFoundException> {
  override fun get(): UserNotFoundException = UserNotFoundException(message)
}
