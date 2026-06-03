package uk.gov.justice.digital.hmpps.prisonusersapi.service.converters

import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserBasicDetails
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserCaseloadDetail
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.AccountStatus
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccount

fun UserAccount.toUserCaseloadDetail(removeDpsCaseload: Boolean = false): UserCaseloadDetail = UserCaseloadDetail(
  username = this.username,
  activeCaseload = this.activeCaseload?.toPrisonCaseload(),
  active = AccountStatus.activeStatuses().contains(this.accountStatus),
  accountType = this.accountType,
  caseloads = this.userAccessibleCaseloads
    .filterNot { (removeDpsCaseload && it.caseload.isDpsCaseload()) }
    .map { uc ->
      uc.caseload.toPrisonCaseload()
    },
)

fun UserAccount.toUserBasicDetails(): UserBasicDetails = UserBasicDetails(
  username = this.username,
  staffId = this.user.legacyStaffId,
  firstName = this.user.firstName.capitalizeFully(),
  lastName = this.user.lastName.capitalizeFully(),
  activeCaseloadId = this.activeCaseload?.id,
  enabled = this.isEnabled(),
  accountStatus = this.accountStatus.name,
)
