package uk.gov.justice.digital.hmpps.prisonusersapi.service

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonusersapi.data.EmailHolder

@Component
class PrimaryEmailDetector {

  fun getPrimaryEmail(emails: List<EmailHolder>): String? = emails.firstOrNull { it.email?.endsWith("@justice.gov.uk") == true }?.email
    ?: emails.firstOrNull()?.email
}