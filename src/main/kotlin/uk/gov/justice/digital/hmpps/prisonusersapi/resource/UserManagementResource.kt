package uk.gov.justice.digital.hmpps.prisonusersapi.resource

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonusersapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserDetails
import uk.gov.justice.digital.hmpps.prisonusersapi.service.AdditionalInformation
import uk.gov.justice.digital.hmpps.prisonusersapi.service.EventPublishService
import uk.gov.justice.digital.hmpps.prisonusersapi.service.PrisonUserDomainEventType
import uk.gov.justice.digital.hmpps.prisonusersapi.service.UserService

@RestController
@Validated
@RequestMapping("/users", produces = [MediaType.APPLICATION_JSON_VALUE])
class UserManagementResource(
  private val userService: UserService,
  private val eventPublishService: EventPublishService,
) {

  @PreAuthorize("hasRole('ROLE_MANAGE_NOMIS_USER_ACCOUNT')")
  @PutMapping("/{username}/unlock-user")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Unlock user account",
    description = "Unlocks the user account. Requires role ROLE_MANAGE_NOMIS_USER_ACCOUNT",
    security = [SecurityRequirement(name = "MANAGE_NOMIS_USER_ACCOUNT")],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "User account unlocked",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to unlock user",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to unlock a user",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "Data integrity violation or sync lock could not be acquired within max wait time",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun unlockUser(
    @Schema(description = "Username", example = "testuser1", required = true)
    @PathVariable
    @Size(max = 30, min = 1, message = "username must be between 1 and 30")
    username: String,
  ) {
    val userAccount = userService.unlockUser(username)
    eventPublishService.publishEvent(
      eventType = PrisonUserDomainEventType.USER_ACCOUNT_UNLOCKED,
      additionalInformation = AdditionalInformation(
        userId = userAccount.user.userId!!,
        legacyStaffId = userAccount.user.legacyStaffId,
        username = userAccount.username,
      ),
    )
  }

  @PreAuthorize("hasRole('ROLE_MAINTAIN_ACCESS_ROLES_ADMIN')")
  @PutMapping("/{username}/change-name")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Change staff name of user account",
    description = "Change staff name of user account. Requires role ROLE_MAINTAIN_ACCESS_ROLES_ADMIN",
    security = [SecurityRequirement(name = "MAINTAIN_ACCESS_ROLES_ADMIN")],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "User account name changed",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to change name of user",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to change the name a user",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "Data integrity violation or sync lock could not be acquired within max wait time",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun changeName(
    @Schema(description = "Username", example = "TEST_USER1", required = true)
    @PathVariable
    @Size(max = 30, min = 1, message = "username must be between 1 and 30 characters")
    username: String,
    @Schema(description = "Staff name details", required = true) @RequestBody @Valid
    nameDetails: NameDetail,
  ): UserDetails {
    val (userDetails, userAccount) = userService.updateFirstAndLastName(
      username = username,
      firstName = nameDetails.firstName,
      lastName = nameDetails.lastName,
    )

    eventPublishService.publishEvent(
      eventType = PrisonUserDomainEventType.USER_NAME_UPDATED,
      additionalInformation = AdditionalInformation(
        userId = userAccount.user.userId!!,
        legacyStaffId = userDetails.staffId,
        username = userAccount.username,
      ),
    )

    return userDetails
  }

  @PreAuthorize("hasRole('ROLE_MAINTAIN_ACCESS_ROLES_ADMIN')")
  @PutMapping("/{username}/change-email", consumes = [MediaType.TEXT_PLAIN_VALUE])
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Change email of user account",
    description = "Change email of user account. Requires role ROLE_MAINTAIN_ACCESS_ROLES_ADMIN",
    security = [SecurityRequirement(name = "MAINTAIN_ACCESS_ROLES_ADMIN")],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "User account email changed",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect request to change email of user",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to change the email a user",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "Data integrity violation or sync lock could not be acquired within max wait time",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun changeEmail(
    @Schema(description = "Username", example = "TEST_USER1", required = true)
    @PathVariable
    @Size(max = 30, min = 1, message = "username must be between 1 and 30 characters")
    username: String,
    @Schema(description = "Email", example = "test@test.com", required = true)
    @Email(message = "Invalid email address")
    @RequestBody
    email: String,
  ): UserDetails {
    val (userDetails, userAccount) = userService.updateEmail(
      username = username,
      email = email,
    )

    eventPublishService.publishEvent(
      eventType = PrisonUserDomainEventType.USER_EMAIL_UPDATED,
      additionalInformation = AdditionalInformation(
        userId = userAccount.user.userId!!,
        legacyStaffId = userDetails.staffId,
        username = userAccount.username,
      ),
    )

    return userDetails
  }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Name Information")
data class NameDetail(
  @Schema(description = "First name of the user", example = "John", required = true)
  @field:Pattern(
    regexp = "^[A-Za-z'-]{1,35}$",
    message = "First name must consist of alphabetical characters, a hyphen or an apostrophe only and a max 35 chars",
  )
  val firstName: String,
  @Schema(description = "Last name of the user", example = "Smith", required = true)
  @field:Pattern(
    regexp = "^[A-Za-z'-]{1,35}$",
    message = "Last name must consist of alphabetical characters, a hyphen or an apostrophe only and a max 35 chars",
  )
  val lastName: String,
)
