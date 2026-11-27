package uk.gov.justice.digital.hmpps.prisonusersapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonusersapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserAccountCaseloadDetail
import uk.gov.justice.digital.hmpps.prisonusersapi.service.AdditionalInformation
import uk.gov.justice.digital.hmpps.prisonusersapi.service.EventPublishService
import uk.gov.justice.digital.hmpps.prisonusersapi.service.PrisonUserDomainEventType
import uk.gov.justice.digital.hmpps.prisonusersapi.service.UserService

@RestController
@Validated
@RequestMapping("/users", produces = [MediaType.APPLICATION_JSON_VALUE])
class UserCaseloadManagementResource(
  private val userService: UserService,
  private val eventPublishService: EventPublishService,
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
  ): UserAccountCaseloadDetail = userService.getCaseloads(username)

  @PreAuthorize("hasRole('ROLE_MAINTAIN_ACCESS_ROLES_ADMIN') or hasRole('ROLE_MAINTAIN_ACCESS_ROLES')")
  @PutMapping("/{username}/default-caseload/{defaultCaseloadId}")
  @Operation(
    summary = "Set the default caseload for this user",
    description = "Sets the default caseload. Caseload must already be present. Requires role ROLE_MAINTAIN_ACCESS_ROLES_ADMIN or ROLE_MAINTAIN_ACCESS_ROLES",
    security = [SecurityRequirement(name = "MAINTAIN_ACCESS_ROLES"), SecurityRequirement(name = "MAINTAIN_ACCESS_ROLES_ADMIN")],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "User information with active caseload details",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to set a default caseload on a user account",
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
        description = "Incorrect permissions to set a default caseload on a user account",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
    ],
  )
  fun setDefaultCaseload(
    @Schema(
      description = "Username to default caseload",
      example = "TEST_USER2",
      required = true,
    )
    @PathVariable
    @Size(max = 30, min = 1, message = "Username must be between 1 and 30")
    username: String,
    @Schema(description = "Default caseload Id", example = "LEI", required = true)
    @PathVariable
    @Size(max = 6, min = 3, message = "Caseload must be between 3 and 6")
    defaultCaseloadId: String,
  ): UserAccountCaseloadDetail {
    val (userAccountCaseloadDetail, userAccount) = userService.setActiveCaseload(username, defaultCaseloadId)

    eventPublishService.publishEvent(
      eventType = PrisonUserDomainEventType.USER_ACCOUNT_ACTIVE_CASELOAD_UPDATED,
      additionalInformation = AdditionalInformation(
        userId = userAccount.user.userId!!,
        legacyStaffId = userAccount.user.legacyStaffId,
        username = userAccount.username,
      ),
    )

    return userAccountCaseloadDetail
  }
}
