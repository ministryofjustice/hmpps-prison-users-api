package uk.gov.justice.digital.hmpps.prisonusersapi.jpa

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import org.hibernate.Hibernate
import org.hibernate.envers.Audited
import org.hibernate.envers.NotAudited
import java.io.Serializable

@Entity
@Table(name = "user_roles")
@Audited
data class UserRole(
  @EmbeddedId
  val id: UserRoleId,

  @ManyToOne
  @MapsId("username")
  @JoinColumn(name = "username")
  @NotAudited
  val userAccount: UserAccount,

  val createdBy: String,
  val createdTimestamp: java.time.LocalDateTime,
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as UserRole

    return id == other.id
  }

  override fun hashCode(): Int = id.hashCode()
}

@Embeddable
data class UserRoleId(
  @Column(name = "username")
  val username: String,

  @Column(name = "role_code")
  val roleCode: String,
) : Serializable
