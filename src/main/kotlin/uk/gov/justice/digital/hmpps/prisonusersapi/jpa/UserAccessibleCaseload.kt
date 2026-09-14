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
import java.time.LocalDateTime

@Entity
@Table(name = "user_accessible_caseloads")
@Audited
data class UserAccessibleCaseload(

  @EmbeddedId
  val id: UserAccessibleCaseloadId,

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

  val createdBy: String,
  val createdTimestamp: LocalDateTime,
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as UserAccessibleCaseload

    return id == other.id
  }

  override fun hashCode(): Int = id.hashCode()
}

@Embeddable
data class UserAccessibleCaseloadId(
  @Column(name = "username")
  val username: String,

  @Column(name = "caseload_id")
  val caseloadId: String,
) : Serializable
