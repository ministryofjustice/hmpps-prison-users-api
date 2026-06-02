package uk.gov.justice.digital.hmpps.prisonusersapi.data

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Basic User Information")
data class UserBasicDetails(
  @Schema(description = "Username", example = "testuser1", required = true) val username: String,
  @Schema(description = "Staff ID", example = "324323", required = true) val staffId: Long,
  @Schema(description = "First name of the user", example = "John", required = true) val firstName: String,
  @Schema(description = "Last name of the user", example = "Smith", required = true) val lastName: String,
  @Schema(description = "Active Caseload of the user", example = "BXI", required = false) val activeCaseloadId: String?,
  @Schema(description = "User is enabled flag", required = true) val enabled: Boolean,
  @Schema(description = "Status of the user", example = "OPEN", required = false) val accountStatus: String?,
)
