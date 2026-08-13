package uk.gov.justice.digital.hmpps.prisonusersapi.service.converters

import uk.gov.justice.digital.hmpps.prisonusersapi.data.AccountStatus
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserBasicDetails
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserCaseloadDetail
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserDetail
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

fun UserAccount.toUserDetail(): UserDetail = UserDetail(
  username = this.username,
  staffId = this.user.legacyStaffId,
  firstName = this.user.firstName.capitalizeFully(),
  lastName = this.user.lastName.capitalizeFully(),
  activeCaseloadId = this.activeCaseload?.id,
  accountStatus = this.accountStatus,
  accountType = this.accountType,
  primaryEmail = this.user.primaryEmail(),
  dpsRoleCodes = this.userRoleCodes.map { it.id.roleCode }.sorted(),
  administratorOfUserGroups = listOf(),
  accountNonLocked = this.isAccountNonLocked(),
  credentialsNonExpired = this.isCredentialsNonExpired(),
  enabled = this.isEnabled(),
  admin = this.isAdmin(),
  active = this.isActive(),
  staffStatus = this.user.status.name,
  lastLogonDate = this.lastLoggedIn,
)
