package uk.gov.justice.digital.hmpps.prisonusersapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonusersapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserMigrationRequest
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserMigrationResponse
import uk.gov.justice.digital.hmpps.prisonusersapi.service.MigrationService

@RestController
@Validated
@RequestMapping("/migrate")
class MigrationResource(
    private val migrationService: MigrationService
) {

    @PreAuthorize("hasAnyRole('ROLE_NOMIS_MIGRATE')") // TODO what role should this be?
    @PostMapping("/user")
    @ResponseStatus(HttpStatus.OK, reason = "User created with associated accounts, role and caseload links from NOMIS")
    @Operation(
        summary = "Migrate a single user from NOMIS into Prison Users API, including associated accounts, role and caseload link data",
        description = "Creates a user. Requires role ROLE_NOMIS_MIGRATE",
        security = [SecurityRequirement(name = "NOMIS_MIGRATE")],
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = UserMigrationRequest::class),
                ),
            ],
        ),
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "User created with associated accounts, role and caseload links from NOMIS",
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request payload",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "Conflict (eg. duplicate user or username)",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized to access this endpoint",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Incorrect permissions to create a user",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun migrateUser(
        @RequestBody @Valid
        userMigrationRequest: UserMigrationRequest,
    ): ResponseEntity<UserMigrationResponse> {
        migrationService.migrateUser(userMigrationRequest)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }
}
