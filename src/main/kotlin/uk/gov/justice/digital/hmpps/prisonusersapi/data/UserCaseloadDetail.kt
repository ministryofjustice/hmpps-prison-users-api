package uk.gov.justice.digital.hmpps.prisonusersapi.data

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "User & Caseload Information")
data class UserCaseloadDetail(
    @Schema(description = "Username", example = "TESTUSER1", required = true) val username: String,
    @Schema(description = "Indicates that the user is active", example = "true", required = true) val active: Boolean,
    @Schema(
        description = "Type of user account",
        example = "GENERAL",
        required = true,
    ) val accountType: UsageType = UsageType.GENERAL,
    @Schema(
        description = "Active Caseload of the user",
        example = "BXI",
        required = false,
    ) val activeCaseload: PrisonCaseload?,
    @Schema(
        description = "Caseloads available for this user",
        required = false,
    ) val caseloads: List<PrisonCaseload> = listOf(),
)