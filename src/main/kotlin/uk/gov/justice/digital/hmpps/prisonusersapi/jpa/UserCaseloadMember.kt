package uk.gov.justice.digital.hmpps.prisonusersapi.jpa

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import org.hibernate.envers.Audited
import org.hibernate.envers.NotAudited
import java.io.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "user_caseload_members")
@Audited
data class UserCaseloadMember(

  @EmbeddedId
  val id: UserCaseloadMemberId,

  @ManyToOne
  @MapsId("caseloadId")
  @JoinColumn(name = "caseload_id")
  @NotAudited
  val caseload: Caseload,

  @ManyToOne
  @MapsId("username")
  @JoinColumn(name = "username")
  @NotAudited
  val userAccount: UserAccount,

  val startDate: LocalDate? = null,
  val expiryDate: LocalDate? = null,
  val active: Boolean? = null,
  val createdBy: String,
  val createdTimestamp: LocalDateTime,
  val modifiedBy: String? = null,
  val modifiedTimestamp: LocalDateTime? = null,
)

@Embeddable
data class UserCaseloadMemberId(
  @Column(name = "username")
  val username: String,

  @Column(name = "caseload_id")
  val caseloadId: String,
) : Serializable
