package uk.gov.justice.digital.hmpps.prisonusersapi.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.Hibernate
import org.hibernate.annotations.UuidGenerator
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserStatus
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "users")
data class User(

  @Id
  @GeneratedValue
  @UuidGenerator
  val userId: UUID? = null,

  @Column(name = "entra_uuid")
  val entraUUID: UUID? = null,

  @OneToMany(
    mappedBy = "user",
    cascade = [CascadeType.ALL],
    orphanRemoval = true,
  )
  val userEmails: MutableList<UserEmail> = mutableListOf(),

  val firstName: String,
  val lastName: String,
  val status: UserStatus,
  val legacyStaffId: Long,
  val createdTimestamp: LocalDateTime,
  val createdBy: String,
  val modifiedTimestamp: LocalDateTime? = null,
  val modifiedBy: String? = null,
) {

  fun addUserEmail(userEmail: UserEmail) {
    userEmails.add(userEmail)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as User

    return userId == other.userId
  }

  override fun hashCode(): Int = userId?.hashCode() ?: 0
}
