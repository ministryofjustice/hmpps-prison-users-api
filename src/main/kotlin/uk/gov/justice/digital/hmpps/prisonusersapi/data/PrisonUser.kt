package uk.gov.justice.digital.hmpps.prisonusersapi.data

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "The full User representation of a migrated NOMIS staff record migrated to a ", type = "object")
data class PrisonUser(
  @Schema(required = true, description = "The unique identifier for the prison user", type = "string", format = "uuid", example = "f59dad25-3f11-4a74-9e03-64d2ee53f498")
  val userId: UUID,

  @Schema(required = true, description = "The NOMIS staff id", type = "integer", format = "int64", example = "123456")
  val staffId: Long,

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

  @Schema(required = false, description = "The email addresses for the user")
  val emails: List<PrisonUserEmail>?,

  @Schema(required = true, description = "The list of accounts associated with the user")
  val accounts: List<PrisonUserAccount>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "User account details", type = "object")
data class PrisonUserAccount(
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

  @Schema(required = false, description = "The caseloads assigned to the user account")
  val caseloads: List<PrisonUserCaseload>,

  @Schema(required = false, description = "The roles assigned to the user account")
  val roles: List<PrisonUserRole>,

  @Schema(description = "The timestamp of the last login for the account", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  val lastLoggedIn: LocalDateTime? = null,

  @Schema(description = "Identifier for the active caseload for the account", type = "string", example = "MRI")
  val activeCaseloadId: String? = null,

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
data class PrisonUserEmail(

  @Schema(required = true, description = "The email address for the user", type = "string", example = "test@email.com")
  var email: String? = null,

  @Schema(required = true, description = "Record creation timestamp", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  var createdTimestamp: LocalDateTime? = null,

  @Schema(required = true, description = "Username of the record creator", type = "string", example = "TEST_USER")
  var createdBy: String? = null,

  @Schema(description = "Record modification timestamp", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  val modifiedTimestamp: LocalDateTime? = null,

  @Schema(description = "Username of the record modifier", type = "string", example = "TEST_USER")
  val modifiedBy: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Caseload assigned to a user account", type = "object")
data class PrisonUserCaseload(
  @Schema(required = true, description = "Identifier for the caseload the user account can access", type = "string", example = "MRI")
  val caseloadId: String,

  @Schema(required = true, description = "Record creation timestamp", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  val createdTimestamp: LocalDateTime,

  @Schema(required = true, description = "Username of the record creator", type = "string", example = "TEST_USER")
  val createdBy: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Role assigned to a user account", type = "object")
data class PrisonUserRole(
  @Schema(required = true, description = "The role code the user account will be assigned", type = "string", example = "TEST_ROLE")
  val roleCode: String,

  @Schema(required = true, description = "Record creation timestamp", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  val createdTimestamp: LocalDateTime,

  @Schema(required = true, description = "Username of the record creator", type = "string", example = "TEST_USER")
  val createdBy: String,
)
