package uk.gov.justice.digital.hmpps.prisonusersapi.service

import org.springframework.stereotype.Component

@Component
class PrimaryEmailDetector {

  fun getPrimaryEmail(emails: List<EmailHolder>): String? = emails.firstOrNull { it.email?.endsWith("@justice.gov.uk") == true }?.email
    ?: emails.firstOrNull()?.email
}

interface EmailHolder {
  val email: String?
}
