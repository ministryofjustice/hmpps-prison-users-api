package uk.gov.justice.digital.hmpps.prisonusersapi.integration.helper

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonusersapi.data.AccountStatus
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UsageType
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserStatus
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.Caseload
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.User
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccessibleCaseload
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccessibleCaseloadId
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccount
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserAccountRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UsersRepository
import java.time.LocalDateTime
import java.util.UUID

@Component
class DataBuilder(
  private val userRepository: UsersRepository,
  private val userAccountRepository: UserAccountRepository,
) {
  fun generalUser() = generalUserEntityCreator(
    userRepository = userRepository,
    userAccountRepository = userAccountRepository,
  )

  fun deleteAll() {
    userRepository.deleteAll()
    userRepository.flush()
  }
}

fun generalUserEntityCreator(
  userRepository: UsersRepository,
  userAccountRepository: UserAccountRepository,
  userAccount: UserAccount = defaultGeneralUserAccount(),
  prisonCodes: List<String> = listOf("WWI"),
): GeneralUserBuilder = GeneralUserBuilder(
  usersRepository = userRepository,
  userAccountRepository = userAccountRepository,
  userAccount = userAccount,
  prisonCodes = prisonCodes,
)

fun defaultGeneralUserAccount(): UserAccount = UserAccount(
  username = "TEST_USER1",
  user = defaultUser(),
  accountType = UsageType.GENERAL,
  accountStatus = AccountStatus.OPEN,
  activeCaseload = Caseload("WWI", "WWI", "GENERAL", administrationCaseload = true, active = true, createdBy = "TEST"),
  userAccessibleCaseloads = mutableListOf(),
  createdBy = "TEST",
  createdTimestamp = LocalDateTime.now(),
)

fun defaultUser(): User = User(
  entraUUID = UUID.randomUUID(),
  legacyStaffId = 123456,
  status = UserStatus.ACTIVE,
  firstName = "John",
  lastName = "Smith",
  createdBy = "TEST",
  createdTimestamp = LocalDateTime.now(),
)

class GeneralUserBuilder(
  usersRepository: UsersRepository,
  userAccountRepository: UserAccountRepository,
  userAccount: UserAccount,
  prisonCodes: List<String>,
) : UserAccountBuilder<GeneralUserBuilder>(
  userAccountRepository,
  usersRepository,
  userAccount,
  prisonCodes,
) {

  override fun build(): GeneralUserBuilder {
    val caseloads = prisonCodes.map {
      val caseload = Caseload(it, "Description for $it", "Function for $it", administrationCaseload = true, active = true, createdBy = "TEST")
      UserAccessibleCaseload(
        UserAccessibleCaseloadId(userAccount.username, it),
        caseload,
        userAccount,
        "TEST",
        createdTimestamp = LocalDateTime.now(),
      )
    }.toMutableList()
    userAccount = userAccount.copy(userAccessibleCaseloads = caseloads, activeCaseload = caseloads[0].caseload)
    return this
  }
}

abstract class UserAccountBuilder<T>(
  private val userAccountRepository: UserAccountRepository,
  private val userRepository: UsersRepository,
  internal var userAccount: UserAccount,
  internal var prisonCodes: List<String>,
) {

  fun save(): UserAccount {
    userAccountRepository.saveAndFlush(userAccount)
    return userAccount
  }

  abstract fun build(): UserAccountBuilder<T>

  fun buildAndSave(): UserAccount {
    build()
    userRepository.saveAndFlush(userAccount.user)
    userAccountRepository.saveAndFlush(userAccount)
    return userAccount
  }

  fun username(username: String): UserAccountBuilder<T> {
    this.userAccount = userAccount.copy(username = username)
    return this
  }

  fun atPrisons(prisonCodes: List<String>): UserAccountBuilder<T> {
    this.prisonCodes = prisonCodes
    return this
  }

  fun atPrisons(vararg prisonCodes: String): UserAccountBuilder<T> = atPrisons(prisonCodes.toList())
  fun atPrison(prisonCode: String): UserAccountBuilder<T> = atPrisons(prisonCode)

  fun firstName(firstName: String): UserAccountBuilder<T> {
    this.userAccount = userAccount.copy(user = userAccount.user.copy(firstName = firstName))
    return this
  }

  fun lastName(lastName: String): UserAccountBuilder<T> {
    this.userAccount = userAccount.copy(user = userAccount.user.copy(lastName = lastName))
    return this
  }

  fun status(status: AccountStatus): UserAccountBuilder<T> {
    this.userAccount =
      userAccount.copy(accountStatus = status)
    return this
  }
}
