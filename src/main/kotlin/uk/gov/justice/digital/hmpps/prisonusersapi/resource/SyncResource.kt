package uk.gov.justice.digital.hmpps.prisonusersapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonusersapi.config.ErrorResponse

@RestController
@RequestMapping("/sync")
class SyncResource {

  @PreAuthorize("hasRole('ROLE_PRISON_USERS_API__SYNC__RW')")
  @PutMapping("/user/{legacyStaffId}")
  @Operation(
    summary = "Sync user creation/updates from NOMIS to Prison Users API",
    description = "Creates or updates a Prison User by the legacy staffId. Requires role ROLE_PRISON_USERS_API__SYNC__RW",
    security = [SecurityRequirement(name = "ROLE_PRISON_USERS_API__SYNC__RW")],
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = PrisonUserSyncRequest::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "204",
        description = "Prison User sync successful.",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Invalid request",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getPrisonUser(
    @Schema(description = "The legacy NOMIS staff Id", example = "123456", required = true)
    @PathVariable
    legacyStaffId: Long,
  ): ResponseEntity<Void> = ResponseEntity.noContent().build()
}
