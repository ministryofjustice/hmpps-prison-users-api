package uk.gov.justice.digital.hmpps.prisonusersapi.service.converters

import uk.gov.justice.digital.hmpps.prisonusersapi.data.reconciliation.PrisonUserAccount
import uk.gov.justice.digital.hmpps.prisonusersapi.data.reconciliation.PrisonUserCaseload
import uk.gov.justice.digital.hmpps.prisonusersapi.data.reconciliation.PrisonUserEmail
import uk.gov.justice.digital.hmpps.prisonusersapi.data.reconciliation.PrisonUserReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonusersapi.data.reconciliation.PrisonUserRole
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.User
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccount
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserRole

fun User.toPrisonUserReconciliationResponse(
  accounts: List<UserAccount>,
  userRolesByUsername: Map<String, List<UserRole>>,
): PrisonUserReconciliationResponse = PrisonUserReconciliationResponse(
  userId = requireNotNull(userId),
  staffId = legacyStaffId,
  firstName = firstName,
  lastName = lastName,
  status = status,
  createdTimestamp = createdTimestamp,
  createdBy = createdBy,
  modifiedTimestamp = modifiedTimestamp,
  modifiedBy = modifiedBy,
  emails = userEmails.sortedWith(compareByDescending<uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserEmail> { it.isPrimary }.thenBy { it.email }).map { userEmail ->
    PrisonUserEmail(
      emailId = requireNotNull(userEmail.id),
      email = userEmail.email,
      isPrimary = userEmail.isPrimary,
      createdTimestamp = userEmail.createdTimestamp,
      createdBy = userEmail.createdBy,
      modifiedTimestamp = userEmail.modifiedTimestamp,
      modifiedBy = userEmail.modifiedBy,
    )
  },
  accounts = accounts.sortedBy { it.username }.map { account ->
    PrisonUserAccount(
      username = account.username,
      accountType = account.accountType,
      accountStatus = account.accountStatus,
      caseloads = account.userAccessibleCaseloads
        .sortedBy { it.caseload.id }
        .map { caseload ->
          PrisonUserCaseload(
            caseloadId = caseload.caseload.id,
            createdTimestamp = caseload.createdTimestamp,
            createdBy = caseload.createdBy,
          )
        },
      roles = userRolesByUsername[account.username].orEmpty()
        .sortedBy { it.id.roleCode }
        .map { role ->
          PrisonUserRole(
            roleCode = role.id.roleCode,
            createdTimestamp = role.createdTimestamp,
            createdBy = role.createdBy,
          )
        },
      activeCaseloadId = account.activeCaseload?.id,
      createdTimestamp = account.createdTimestamp,
      createdBy = account.createdBy,
      modifiedTimestamp = account.modifiedTimestamp,
      modifiedBy = account.modifiedBy,
    )
  },
)

