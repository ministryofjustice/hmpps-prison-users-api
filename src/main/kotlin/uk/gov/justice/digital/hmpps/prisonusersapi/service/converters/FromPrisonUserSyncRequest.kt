package uk.gov.justice.digital.hmpps.prisonusersapi.service.converters

import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.PrisonUserSyncRequest
import uk.gov.justice.digital.hmpps.prisonusersapi.data.sync.SyncPrisonUserEmail
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.User
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
        user = user,
      ),
    )
  }

  return user
}
