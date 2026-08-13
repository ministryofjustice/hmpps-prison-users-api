package uk.gov.justice.digital.hmpps.prisonusersapi.data

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "User Group Information")
data class UserGroupDetail(
  @Schema(description = "Group id", example = "BXI", required = true) val id: String,
  @Schema(description = "Group name", example = "Brixton (HMP)", required = true) val name: String,
)
