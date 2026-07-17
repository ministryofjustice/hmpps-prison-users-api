package uk.gov.justice.digital.hmpps.prisonusersapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonusersapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonusersapi.data.reconciliation.PrisonUserReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonusersapi.service.ReconciliationService

@RestController
@RequestMapping("/reconciliation")
class ReconciliationResource(
  private val reconciliationService: ReconciliationService,
) {

  @PreAuthorize("hasRole('ROLE_PRISON_USERS_API__MIGRATION__RW')")
  @GetMapping("/user/{legacyStaffId}")
  @Operation(
    summary = "Get the details of a migrated user by the legacy staff Id",
    description = "Get the full view of a migrated user. Requires role ROLE_PRISON_USERS_API__MIGRATION__RW",
    security = [SecurityRequirement(name = "PRISON_USERS_API__MIGRATION__RW")],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns the prison user data.",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = PrisonUserReconciliationResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Invalid request",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "User not found",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
    ],
  )
  fun getPrisonUserForReconciliation(
    @Schema(description = "The legacy NOMIS staff Id", example = "123456", required = true)
    @PathVariable
    legacyStaffId: Long,
  ): ResponseEntity<PrisonUserReconciliationResponse> = ResponseEntity.ok(reconciliationService.getPrisonUserForReconciliation(legacyStaffId))
}
