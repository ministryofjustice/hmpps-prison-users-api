package uk.gov.justice.digital.hmpps.prisonusersapi.data

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UserAdministeredBy(
  @Schema(description = "Username", example = "testuser1") val username: String,
  @Schema(description = "Local administrator's username", example = "testlsa1")
  val localAdministratorUsername: String,
  @Schema(
    description = "True if the user belongs to a local admin authority the local administrator manages",
    example = "true",
  )
  val administered: Boolean,
)
