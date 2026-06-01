package uk.gov.justice.digital.hmpps.prisonusersapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonusersapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserCaseloadDetail
import uk.gov.justice.digital.hmpps.prisonusersapi.service.UserService

@RestController
@Validated
@RequestMapping("/users", produces = [MediaType.APPLICATION_JSON_VALUE])
class UserCaseloadManagementResource(
  private val userService: UserService,
) {

  @PreAuthorize("hasAnyRole('ROLE_MAINTAIN_ACCESS_ROLES_ADMIN','ROLE_MAINTAIN_ACCESS_ROLES','ROLE_VIEW_NOMIS_STAFF_DETAILS')")
  @GetMapping("/{username}/caseloads")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get list of caseloads associated with the users account",
    description = "Caseloads for a specific user. Requires role ROLE_MAINTAIN_ACCESS_ROLES_ADMIN or ROLE_MAINTAIN_ACCESS_ROLES or ROLE_VIEW_NOMIS_STAFF_DETAILS",
    security = [SecurityRequirement(name = "MAINTAIN_ACCESS_ROLES"), SecurityRequirement(name = "MAINTAIN_ACCESS_ROLES_ADMIN"), SecurityRequirement(name = "VIEW_NOMIS_STAFF_DETAILS")],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "User caseload list",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to get caseloads for a user",
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
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to get a caseload for a user",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
    ],
  )
  fun getUserCaseloads(
    @Schema(description = "Username", example = "TEST_USER1", required = true)
    @PathVariable
    username: String,
  ): UserCaseloadDetail = userService.getCaseloads(username)
}
