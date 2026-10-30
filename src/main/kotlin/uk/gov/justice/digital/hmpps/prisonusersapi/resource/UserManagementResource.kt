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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonusersapi.config.ErrorResponse

@RestController
@Validated
@RequestMapping("/users", produces = [MediaType.APPLICATION_JSON_VALUE])
class UserManagementResource {

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
        ],
    )
    fun unlockUser(
        @Schema(description = "Username", example = "testuser1", required = true)
        @PathVariable
        @Size(max = 30, min = 1, message = "username must be between 1 and 30")
        username: String,
    ) {

    }
}