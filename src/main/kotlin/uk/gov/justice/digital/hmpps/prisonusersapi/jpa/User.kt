package uk.gov.justice.digital.hmpps.prisonusersapi.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.NamedAttributeNode
import jakarta.persistence.NamedEntityGraph
import jakarta.persistence.NamedSubgraph
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.Hibernate
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UsageType
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