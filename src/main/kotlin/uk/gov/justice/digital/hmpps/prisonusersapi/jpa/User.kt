package uk.gov.justice.digital.hmpps.prisonusersapi.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "users")
data class User(

  @Id
  val userId: UUID,

  @Column(name = "entra_uuid")
  val entraUUID: UUID,
  val email: String,
  val firstName: String,
  val lastName: String,
  val status: String,
  val legacyStaffId: Long,
  val createdBy: String,
)
