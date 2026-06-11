package uk.gov.justice.digital.hmpps.prisonusersapi.jpa

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable

@Entity
@Table(name = "user_roles")
data class UserRole(
  @EmbeddedId
  val id: UserRoleId,
  val createdBy: String,
  val createdTimestamp: java.time.LocalDateTime,
)

@Embeddable
data class UserRoleId(
  @Column(name = "username")
  val username: String,

  @Column(name = "role_code")
  val roleCode: String,
) : Serializable
