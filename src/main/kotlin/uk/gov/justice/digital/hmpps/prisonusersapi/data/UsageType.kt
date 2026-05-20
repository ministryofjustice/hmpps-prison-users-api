package uk.gov.justice.digital.hmpps.prisonusersapi.data

enum class UsageType {
  GENERAL,
  ADMIN,
  ;

  companion object {
    fun from(value: String?): UsageType? = value?.let { v -> entries.find { it.name.equals(v, ignoreCase = true) } }
  }
}

fun getUsageType(adminOnly: Boolean) = if (adminOnly) {
  UsageType.ADMIN
} else {
  UsageType.GENERAL
}
