package uk.gov.justice.digital.hmpps.prisonusersapi.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import org.hibernate.Hibernate
import org.hibernate.envers.Audited
import org.hibernate.envers.NotAudited
import java.time.LocalDateTime

@Entity
@Table(name = "user_emails")
@Audited
data class UserEmail(
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_email_id_seq")
  @SequenceGenerator(name = "user_email_id_seq", sequenceName = "user_emails_id_seq", allocationSize = 1)
  val id: Long? = null,

  @Column(name = "email")
  val email: String,

  val isPrimary: Boolean,
  val createdBy: String,
  val createdTimestamp: LocalDateTime,
  val modifiedBy: String? = null,
  val modifiedTimestamp: LocalDateTime? = null,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  @NotAudited
  val user: User,
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as UserEmail

    return id != null && id == other.id
  }

  override fun hashCode(): Int = id?.hashCode() ?: 0
}
