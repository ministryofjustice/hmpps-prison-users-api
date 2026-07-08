package uk.gov.justice.digital.hmpps.prisonusersapi.data.reconciliation

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonusersapi.data.AccountStatus
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UsageType
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserStatus
import java.time.LocalDateTime
import java.util.Collections.emptyList
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "The full User representation of a migrated NOMIS staff record migrated to a ", type = "object")
data class PrisonUserReconciliationResponse(
  @Schema(description = "The unique identifier for the prison user", type = "string", format = "uuid", example = "f59dad25-3f11-4a74-9e03-64d2ee53f498")
  val userId: UUID,

  @Schema(description = "The NOMIS staff id", type = "integer", format = "int64", example = "123456")
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

  @Schema(description = "The email addresses for the user")
  val emails: List<PrisonUserEmail> = emptyList(),

  @Schema(description = "The list of accounts associated with the user")
  val accounts: List<PrisonUserAccount> = emptyList(),
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "User email details", type = "object")
data class PrisonUserEmail(
  @Schema(description = "The id of the email record", example = "123456")
  var emailId: Long,

  @Schema(description = "The email address for the user", type = "string", example = "test@email.com")
  var email: String,

  @Schema(description = "Boolean indicating if this is the primary email address for the user", type = "string", example = "true")
  var isPrimary: Boolean,

  @Schema(description = "Record creation timestamp", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  var createdTimestamp: LocalDateTime,

  @Schema(description = "Username of the record creator", type = "string", example = "TEST_USER")
  var createdBy: String,

  @Schema(description = "Record modification timestamp", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  val modifiedTimestamp: LocalDateTime? = null,

  @Schema(description = "Username of the record modifier", type = "string", example = "TEST_USER")
  val modifiedBy: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "User account details", type = "object")
data class PrisonUserAccount(
  @Schema(description = "Username for the user account", type = "string", example = "TEST_USER")
  val username: String,

  @Schema(description = "The user account type e.g. ADMIN/GENERAL", type = "string", allowableValues = ["ADMIN", "GENERAL"])
  val accountType: UsageType,

  @Schema(
    description = "The user account status indicating if it is open, locked or expired",
    type = "string",
    allowableValues = ["OPEN", "EXPIRED", "EXPIRED_GRACE", "LOCKED_TIMED", "LOCKED", "EXPIRED_LOCKED_TIMED", "EXPIRED_GRACE_LOCKED_TIMED", "EXPIRED_LOCKED", "EXPIRED_GRACE_LOCKED"],
  )
  val accountStatus: AccountStatus,

  @Schema(description = "The caseloads assigned to the user account")
  val caseloads: List<PrisonUserCaseload> = emptyList(),

  @Schema(description = "The roles assigned to the user account")
  val roles: List<PrisonUserRole> = emptyList(),

  @Schema(description = "The timestamp of the last login for the account", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  val lastLoggedIn: LocalDateTime? = null,

  @Schema(description = "Identifier for the active caseload for the account", type = "string", example = "MRI")
  val activeCaseloadId: String? = null,

  @Schema(description = "Record creation timestamp", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  val createdTimestamp: LocalDateTime,

  @Schema(description = "Username of the record creator", type = "string", example = "TEST_USER")
  val createdBy: String,

  @Schema(description = "Record modification timestamp", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  val modifiedTimestamp: LocalDateTime? = null,

  @Schema(description = "Username of the record modifier", type = "string", example = "TEST_USER")
  val modifiedBy: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Role assigned to a user account", type = "object")
data class PrisonUserRole(
  @Schema(description = "The role code the user account will be assigned", type = "string", example = "TEST_ROLE")
  val roleCode: String,

  @Schema(description = "Record creation timestamp", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  val createdTimestamp: LocalDateTime,

  @Schema(description = "Username of the record creator", type = "string", example = "TEST_USER")
  val createdBy: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Caseload assigned to a user account", type = "object")
data class PrisonUserCaseload(
  @Schema(description = "Identifier for the caseload the user account can access", type = "string", example = "MRI")
  val caseloadId: String,

  @Schema(description = "Record creation timestamp", type = "string", format = "date-time", example = "2022-01-01T12:00:00")
  val createdTimestamp: LocalDateTime,

  @Schema(description = "Username of the record creator", type = "string", example = "TEST_USER")
  val createdBy: String,
)
