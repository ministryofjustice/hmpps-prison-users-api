package uk.gov.justice.digital.hmpps.prisonusersapi.data

import io.swagger.v3.oas.annotations.media.Schema

data class PrisonCaseload(
  @Schema(description = "identify for caseload", example = "WWI")
  val id: String,
  @Schema(description = "name of caseload, typically prison name", example = "WANDSWORTH (HMP)")
  val name: String,
  @Schema(description = "function of caseload", example = "GENERAL")
  val function: String,
)
