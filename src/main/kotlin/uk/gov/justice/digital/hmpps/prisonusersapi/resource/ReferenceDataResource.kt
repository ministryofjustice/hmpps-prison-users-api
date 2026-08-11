package uk.gov.justice.digital.hmpps.prisonusersapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonusersapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonusersapi.data.PrisonCaseload
import uk.gov.justice.digital.hmpps.prisonusersapi.service.ReferenceDataService

@RestController
@RequestMapping("/reference-data", produces = [MediaType.APPLICATION_JSON_VALUE])
class ReferenceDataResource(private val referenceDataService: ReferenceDataService) {
  @GetMapping("/caseloads")
  @Operation(
    summary = "Retrieves all caseloads",
    description = "Retrieves all the current active general caseloads, these are effectively prisons that staff can be associated with",
    responses = [
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getCaseload(): List<PrisonCaseload> = referenceDataService.getActiveGeneralCaseloads()
}
