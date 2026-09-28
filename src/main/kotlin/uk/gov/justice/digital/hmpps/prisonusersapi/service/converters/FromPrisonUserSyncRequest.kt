package uk.gov.justice.digital.hmpps.prisonusersapi.service.converters

import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.PrisonUserSyncRequest
import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.SyncPrisonUserEmail
import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.SyncUserCaseloadAdministrator
import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.SyncUserCaseloadMember
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.Caseload
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.User
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccount
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserCaseloadAdministrator
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserCaseloadAdministratorId
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserCaseloadMember
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserCaseloadMemberId
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserEmail
import uk.gov.justice.digital.hmpps.prisonusersapi.service.PrimaryEmailDetector

fun PrisonUserSyncRequest.toUser(legacyStaffId: Long): User = User(
  firstName = firstName,
  lastName = lastName,
  status = status,
  legacyStaffId = legacyStaffId,
  createdTimestamp = createdTimestamp,
  createdBy = createdBy,
  modifiedTimestamp = modifiedTimestamp,
  modifiedBy = modifiedBy,
  userEmails = mutableListOf(),
)

fun PrisonUserSyncRequest.addEmailsTo(user: User, primaryEmailDetector: PrimaryEmailDetector): User {
  val emails: List<SyncPrisonUserEmail> = this.emails
  val primaryEmail: String? = primaryEmailDetector.getPrimaryEmail(emails)
  emails.forEach {
    user.addUserEmail(
      UserEmail(
        email = it.email,
        isPrimary = it.email == primaryEmail,
        createdBy = it.createdBy,
        createdTimestamp = it.createdTimestamp,
        modifiedBy = it.modifiedBy,
        modifiedTimestamp = it.modifiedTimestamp,
        user = user,
      ),
    )
  }

  return user
}

fun SyncUserCaseloadAdministrator.toUserCaseloadAdministrator(userAccount: UserAccount, caseload: Caseload): UserCaseloadAdministrator = UserCaseloadAdministrator(
  id = UserCaseloadAdministratorId(username = username, caseloadId = caseloadId),
  caseload = caseload,
  userAccount = userAccount,
  active = active,
  expiryDate = expiryDate,
  createdBy = createdBy,
  createdTimestamp = createdTimestamp,
  modifiedBy = modifiedBy,
  modifiedTimestamp = modifiedTimestamp,
)

fun SyncUserCaseloadMember.toUserCaseloadMember(userAccount: UserAccount, caseload: Caseload): UserCaseloadMember = UserCaseloadMember(
  id = UserCaseloadMemberId(username = username, caseloadId = caseloadId),
  caseload = caseload,
  userAccount = userAccount,
  startDate = startDate,
  expiryDate = expiryDate,
  active = active,
  createdBy = createdBy,
  createdTimestamp = createdTimestamp,
  modifiedBy = modifiedBy,
  modifiedTimestamp = modifiedTimestamp,
)

