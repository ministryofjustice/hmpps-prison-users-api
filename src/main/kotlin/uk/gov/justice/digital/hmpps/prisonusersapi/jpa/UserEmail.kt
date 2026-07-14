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
import java.time.LocalDateTime

@Entity
@Table(name = "user_emails")
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
  val user: User,
)
