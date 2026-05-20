package uk.gov.justice.digital.hmpps.prisonusersapi.jpa

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import java.io.Serializable

@Entity
@Table(name = "user_accessible_caseloads")
data class UserAccessibleCaseload(

  @EmbeddedId
  val id: UserAccessibleCaseloadId,

  @ManyToOne
  @MapsId("caseloadId")
  @JoinColumn(name = "caseload_id")
  val caseload: Caseload,

  @ManyToOne
  @MapsId("username")
  @JoinColumn(name = "username")
  val userAccount: UserAccount,
)

@Embeddable
data class UserAccessibleCaseloadId(
  @Column(name = "username")
  val username: String,

  @Column(name = "caseload_id")
  val caseloadId: String,
) : Serializable
