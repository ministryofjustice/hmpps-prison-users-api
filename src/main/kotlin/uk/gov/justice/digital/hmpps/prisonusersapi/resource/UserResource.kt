package uk.gov.justice.digital.hmpps.prisonusersapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonusersapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonusersapi.service.UserService

@RestController
@Validated
@RequestMapping("/users", produces = [MediaType.APPLICATION_JSON_VALUE])
class UserResource(
  private val userService: UserService,
) {
  @PreAuthorize("hasAnyRole('ROLE_MAINTAIN_ACCESS_ROLES_ADMIN', 'ROLE_MAINTAIN_ACCESS_ROLES', 'ROLE_MANAGE_NOMIS_USER_ACCOUNT', 'ROLE_VIEW_NOMIS_STAFF_DETAILS')")
  @GetMapping("/basic/{username}")
  @Operation(
    summary = "Get user basic details",
    description = "Information on a specific user. Requires role ROLE_MAINTAIN_ACCESS_ROLES_ADMIN or ROLE_MAINTAIN_ACCESS_ROLES or ROLE_MANAGE_NOMIS_USER_ACCOUNT or ROLE_VIEW_NOMIS_STAFF_DETAILS",
    security = [
      SecurityRequirement(name = "MAINTAIN_ACCESS_ROLES_ADMIN"), SecurityRequirement(name = "MAINTAIN_ACCESS_ROLES"),
      SecurityRequirement(name = "MANAGE_NOMIS_USER_ACCOUNT"), SecurityRequirement(name = "VIEW_NOMIS_STAFF_DETAILS"),
    ],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "User Information Returned",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to get user information",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to get a user",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "user not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getUserBasicDetailsInfo(
    @Schema(description = "Username", example = "testuser1", required = true)
    @PathVariable
    username: String,
  ) = userService.findUserBasicDetails(username)
}
