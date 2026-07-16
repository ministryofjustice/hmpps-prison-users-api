package uk.gov.justice.digital.hmpps.prisonusersapi.data

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Single NOMIS user migration request", type = "object")
data class UserMigrationRequest(
  @Schema(required = true, description = "The NOMIS user to migrate")
  @field:Valid
  var user: MigratedUser,

  @Schema(description = "The NOMIS user accounts to migrate")
  @field:Valid
  val accounts: List<MigratedUserAccount>?,

  @Schema(description = "Links between the user (via user account) and roles")
  @field:Valid
  val roles: List<MigratedUserRole>?,

  @Schema(description = "Links between the user (via user account) and caseloads")
  @field:Valid
  val accessibleCaseloads: List<MigratedUserAccessibleCaseload>?,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Single NOMIS user details", type = "object")
data class MigratedUser(
  @Schema(required = true, description = "The NOMIS staff id", example = "123456")
  val staffId: Long,

  @Schema(required = false, description = "The email addresses for the user")
  @field:Valid
  val emails: List<MigratedUserEmail>?,

  @Schema(required = true, description = "First name of the user", type = "string", example = "John")
  val firstName: String,

  @Schema(required = true, description = "The last name of the user", type = "string", example = "Smith")
  val lastName: String,

  @Schema(required = true, description = "The status of the user e.g. ACTIVE/INACTIVE", type = "string", allowableValues = ["ACTIVE", "INACTIVE"])
  val status: UserStatus,

  @Schema(required = true, description = "Record creation timestamp", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  val createdTimestamp: LocalDateTime,

  @Schema(required = true, description = "Username of the record creator", type = "string", example = "TEST_USER")
  val createdBy: String,

  @Schema(description = "Record modification timestamp", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  val modifiedTimestamp: LocalDateTime? = null,

  @Schema(description = "Username of the record modifier", type = "string", example = "TEST_USER")
  val modifiedBy: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Single NOMIS user email details", type = "object")
data class MigratedUserEmail(

  @Schema(required = true, description = "The email address for the user", type = "string", example = "test@email.com")
  val email: String,

  @Schema(required = true, description = "Record creation timestamp", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  val createdTimestamp: LocalDateTime,

  @Schema(required = true, description = "Username of the record creator", type = "string", example = "TEST_USER")
  val createdBy: String,

  @Schema(description = "Record modification timestamp", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  val modifiedTimestamp: LocalDateTime? = null,

  @Schema(description = "Username of the record modifier", type = "string", example = "TEST_USER")
  val modifiedBy: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "NOMIS User account details", type = "object")
data class MigratedUserAccount(
  @Schema(required = true, description = "Username for the user account", type = "string", example = "TEST_USER")
  val username: String,

  @Schema(required = true, description = "The user account type e.g. ADMIN/GENERAL", type = "string", allowableValues = ["ADMIN", "GENERAL"])
  val accountType: UsageType,

  @Schema(
    required = true,
    description = "The user account status indicating if it is open, locked or expired",
    type = "string",
    allowableValues = ["OPEN", "EXPIRED", "EXPIRED_GRACE", "LOCKED_TIMED", "LOCKED", "EXPIRED_LOCKED_TIMED", "EXPIRED_GRACE_LOCKED_TIMED", "EXPIRED_LOCKED", "EXPIRED_GRACE_LOCKED"],
  )
  val accountStatus: AccountStatus,

  @Schema(required = true, description = "Record creation timestamp", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  val createdTimestamp: LocalDateTime,

  @Schema(required = true, description = "Username of the record creator", type = "string", example = "TEST_USER")
  val createdBy: String,

  @Schema(description = "The timestamp of the last login for the account", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  val lastLoggedIn: LocalDateTime? = null,

  @Schema(description = "Identifier for the active caseload for the account", type = "string", example = "MRI")
  val activeCaseloadId: String? = null,

  @Schema(description = "Record modification timestamp", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  val modifiedTimestamp: LocalDateTime? = null,

  @Schema(description = "Username of the record modifier", type = "string", example = "TEST_USER")
  val modifiedBy: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "NOMIS user account to caseload link", type = "object")
data class MigratedUserAccessibleCaseload(
  @Schema(required = true, description = "Username for the user account", type = "string", example = "TEST_USER")
  val username: String,

  @Schema(required = true, description = "Identifier for the caseload the user account can access", type = "string", example = "MRI")
  val caseloadId: String,

  @Schema(required = true, description = "Record creation timestamp", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  val createdTimestamp: LocalDateTime,

  @Schema(required = true, description = "Username of the record creator", type = "string", example = "TEST_USER")
  val createdBy: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "NOMIS user account to role link", type = "object")
data class MigratedUserRole(
  @Schema(required = true, description = "Username for the user account", type = "string", example = "TEST_USER")
  val username: String,

  @Schema(required = true, description = "The role code the user account will be assigned", type = "string", example = "TEST_ROLE")
  val roleCode: String,

  @Schema(required = true, description = "Record creation timestamp", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  val createdTimestamp: LocalDateTime,

  @Schema(required = true, description = "Username of the record creator", type = "string", example = "TEST_USER")
  val createdBy: String,
)
