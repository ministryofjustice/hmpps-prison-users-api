import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonusersapi.data.AccountStatus
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UsageType
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserStatus
import java.time.LocalDateTime
import java.util.Collections.emptyList

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "NOMIS-to-Prison-User sync request", type = "object")
data class PrisonUserSyncRequest(
  @Schema(
    required = true,
    description = "First name of the user",
    type = "string",
    example = "John",
  )
  val firstName: String,

  @Schema(
    required = true,
    description = "The last name of the user",
    type = "string",
    example = "Smith",
  )
  val lastName: String,

  @Schema(
    required = true,
    description = "The status of the user e.g. ACTIVE/INACTIVE",
    type = "string",
    allowableValues = ["ACTIVE", "INACTIVE"],
  )
  val status: UserStatus,

  @Schema(
    required = true,
    description = "Record creation timestamp",
    type = "string",
    format = "date-time",
    example = "2022-01-01T12:00:00",
  )
  val createdTimestamp: LocalDateTime,

  @Schema(
    required = true,
    description = "Username of the record creator",
    type = "string",
    example = "TEST_USER",
  )
  val createdBy: String,

  @Schema(
    description = "Record modification timestamp",
    type = "string",
    format = "date-time",
    example = "2022-01-01T12:00:00",
  )
  val modifiedTimestamp: LocalDateTime? = null,

  @Schema(description = "Username of the record modifier", type = "string", example = "TEST_USER")
  val modifiedBy: String? = null,

  @Schema(description = "The email addresses for the user")
  val emails: List<SyncPrisonUserEmail> = emptyList(),

  @Schema(description = "The list of accounts associated with the user")
  val accounts: List<SyncPrisonUserAccount> = emptyList(),

)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "User emails to sync from NOMIS", type = "object")
data class SyncPrisonUserEmail(

  @Schema(
    required = true,
    description = "The email address for the user",
    type = "string",
    example = "test@email.com",
  )
  var email: String? = null,

  @Schema(
    required = true,
    description = "Record creation timestamp",
    type = "string",
    format = "date-time",
    example = "2022-01-01T12:00:00",
  )
  var createdTimestamp: LocalDateTime? = null,

  @Schema(
    required = true,
    description = "Username of the record creator",
    type = "string",
    example = "TEST_USER",
  )
  var createdBy: String? = null,

  @Schema(
    description = "Record modification timestamp",
    type = "string",
    format = "date-time",
    example = "2022-01-01T12:00:00",
  )
  val modifiedTimestamp: LocalDateTime? = null,

  @Schema(description = "Username of the record modifier", type = "string", example = "TEST_USER")
  val modifiedBy: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "User account details to sync from NOMIS", type = "object")
data class SyncPrisonUserAccount(
  @Schema(
    required = true,
    description = "Username for the user account",
    type = "string",
    example = "TEST_USER",
  )
  val username: String,

  @Schema(
    required = true,
    description = "The user account type e.g. ADMIN/GENERAL",
    type = "string",
    allowableValues = ["ADMIN", "GENERAL"],
  )
  val accountType: UsageType,

  @Schema(
    required = true,
    description = "The user account status indicating if it is open, locked or expired",
    type = "string",
    allowableValues = ["OPEN", "EXPIRED", "EXPIRED_GRACE", "LOCKED_TIMED", "LOCKED", "EXPIRED_LOCKED_TIMED", "EXPIRED_GRACE_LOCKED_TIMED", "EXPIRED_LOCKED", "EXPIRED_GRACE_LOCKED"],
  )
  val accountStatus: AccountStatus,

  @Schema(required = false, description = "The caseloads assigned to the user account")
  val caseloads: List<SyncPrisonUserCaseload>,

  @Schema(required = false, description = "The roles assigned to the user account")
  val roles: List<SyncPrisonUserRole>,

  @Schema(
    description = "The timestamp of the last login for the account",
    type = "string",
    format = "date-time",
    example = "2022-01-01T12:00:00",
  )
  val lastLoggedIn: LocalDateTime? = null,

  @Schema(
    description = "Identifier for the active caseload for the account",
    type = "string",
    example = "MRI",
  )
  val activeCaseloadId: String? = null,

  @Schema(
    required = true,
    description = "Record creation timestamp",
    type = "string",
    format = "date-time",
    example = "2022-01-01T12:00:00",
  )
  val createdTimestamp: LocalDateTime,

  @Schema(
    required = true,
    description = "Username of the record creator",
    type = "string",
    example = "TEST_USER",
  )
  val createdBy: String,

  @Schema(
    description = "Record modification timestamp",
    type = "string",
    format = "date-time",
    example = "2022-01-01T12:00:00",
  )
  val modifiedTimestamp: LocalDateTime? = null,

  @Schema(description = "Username of the record modifier", type = "string", example = "TEST_USER")
  val modifiedBy: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Role assigned to a user account to sync", type = "object")
data class SyncPrisonUserRole(
  @Schema(
    required = true,
    description = "The role code the user account will be assigned",
    type = "string",
    example = "TEST_ROLE",
  )
  val roleCode: String,

  @Schema(
    required = true,
    description = "Record creation timestamp",
    type = "string",
    format = "date-time",
    example = "2022-01-01T12:00:00",
  )
  val createdTimestamp: LocalDateTime,

  @Schema(
    required = true,
    description = "Username of the record creator",
    type = "string",
    example = "TEST_USER",
  )
  val createdBy: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Caseload assigned to a user account to sync", type = "object")
data class SyncPrisonUserCaseload(
  @Schema(
    required = true,
    description = "Identifier for the caseload the user account can access",
    type = "string",
    example = "MRI",
  )
  val caseloadId: String,

  @Schema(
    required = true,
    description = "Record creation timestamp",
    type = "string",
    format = "date-time",
    example = "2022-01-01T12:00:00",
  )
  val createdTimestamp: LocalDateTime,

  @Schema(
    required = true,
    description = "Username of the record creator",
    type = "string",
    example = "TEST_USER",
  )
  val createdBy: String,
)
