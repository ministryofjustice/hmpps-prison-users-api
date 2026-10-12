package uk.gov.justice.digital.hmpps.prisonusersapi.jpa

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserStatus

@Converter(autoApply = true)
class UserStatusConverter : AttributeConverter<UserStatus, String> {

  override fun convertToDatabaseColumn(userStatus: UserStatus?): String? = userStatus?.name

  override fun convertToEntityAttribute(statusName: String?): UserStatus? = statusName?.let { UserStatus.valueOf(it) }
}
