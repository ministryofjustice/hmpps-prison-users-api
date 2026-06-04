package uk.gov.justice.digital.hmpps.prisonusersapi.data

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response for successful migration of single NOMIS user", type = "object")
data class UserMigrationResponse(

    @Schema(required = true, description = "The unique identifier for the migrated user", type = "string", format = "uuid", example = "f59dad25-3f11-4a74-9e03-64d2ee53f498")
    val userId: String,

    @Schema(required = true, description = "The NOMIS staff id for the migrated user", type = "string", example = "1234567")
    val staffId: String,

    @Schema(required = true, description = "The usernames of the accounts for the migrated user", type = "array", example = "TEST_USER")
    val username: List<String>,
)
