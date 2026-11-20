package uk.gov.justice.digital.hmpps.prisonusersapi.data

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "User Information")
data class UserDetails(
  @Schema(description = "Legacy staff id", example = "324323", required = true) val staffId: Long,
  @Schema(description = "First name of the user", example = "John", required = true) val firstName: String,
  @Schema(description = "Last name of the user", example = "Smith", required = true) val lastName: String,
  @Schema(description = "Status of user account", example = "ACTIVE", required = true) val status: String,
  @Schema(
    description = "Email addresses of user",
    example = "test@test.com",
    required = false,
  ) val primaryEmail: String?,
  @Schema(
    description = "General user account for this user",
    required = false,
  ) val generalAccount: UserAccountCaseloadDetail?,
  @Schema(
    description = "Admin user account for this user",
    required = false,
  ) val adminAccount: UserAccountCaseloadDetail?,
)
