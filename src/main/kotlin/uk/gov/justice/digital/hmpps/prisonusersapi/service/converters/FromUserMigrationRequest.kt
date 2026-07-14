package uk.gov.justice.digital.hmpps.prisonusersapi.service.converters

import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserMigrationRequest
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.Caseload
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.User
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccount
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserEmail

fun UserMigrationRequest.toUser(): User {
  with(this.user!!) {
    val user = User(
      firstName = firstName!!,
      lastName = lastName!!,
      status = status!!,
      legacyStaffId = staffId!!,
      createdTimestamp = createdTimestamp!!,
      createdBy = createdBy!!,
      modifiedTimestamp = modifiedTimestamp,
      modifiedBy = modifiedBy,
    )

    val emails = this.emails.orEmpty()
    val primaryEmail: String? = emails.firstOrNull { it.email?.endsWith("@justice.gov.uk") ?: false }?.email ?: emails.firstOrNull()?.email

    emails.forEach {
      user.addUserEmail(
        UserEmail(
          email = it.email!!,
          isPrimary = it.email == primaryEmail,
          createdBy = it.createdBy!!,
          createdTimestamp = it.createdTimestamp!!,
          user = user,
        ),
      )
    }

    return user
  }
}

fun UserMigrationRequest.toUserAccounts(user: User, mapToActiveCaseload: (activeCaseloadId: String?, username: String) -> Caseload?): List<UserAccount>? = this.accounts?.map { userAccount ->
  UserAccount(
    username = userAccount.username!!,
    user = user,
    accountType = userAccount.accountType!!,
    accountStatus = userAccount.accountStatus!!,
    activeCaseload = mapToActiveCaseload(userAccount.activeCaseloadId, userAccount.username!!),
    createdBy = userAccount.createdBy!!,
    createdTimestamp = userAccount.createdTimestamp!!,
    modifiedBy = userAccount.modifiedBy,
    modifiedTimestamp = userAccount.modifiedTimestamp,
  )
}
