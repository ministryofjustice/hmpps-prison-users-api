package uk.gov.justice.digital.hmpps.prisonusersapi.jpa

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import uk.gov.justice.digital.hmpps.prisonusersapi.data.AccountStatus

@Converter(autoApply = true)
class AccountStatusConverter : AttributeConverter<AccountStatus, String> {

  override fun convertToDatabaseColumn(accountStatus: AccountStatus?): String? = accountStatus?.desc

  override fun convertToEntityAttribute(statusDescription: String?): AccountStatus? = statusDescription?.let { AccountStatus.get(it) }
}
