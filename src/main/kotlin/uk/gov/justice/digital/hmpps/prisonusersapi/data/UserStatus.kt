package uk.gov.justice.digital.hmpps.prisonusersapi.data

enum class UserStatus(val description: String) {
  ACTIVE("Active"),
  CAREER("Career Break"),
  INACT("Inactive"),
  MAT("Maternity Leave"),
  PAT("Paternity Leave"),
  SAB("Sabbatical Leave"),
  SICK("Long Term Sick"),
  SUS("Suspended"),
}
