package uk.gov.justice.digital.hmpps.prisonusersapi.jpa

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import java.io.Serializable
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "user_emails")
data class UserEmail(
    @EmbeddedId
    val id: UserEmailId,

    val isPrimary: Boolean,
    val createdBy: String,
    val createdTimestamp: LocalDateTime,
    val modifiedBy: String? = null,
    val modifiedTimestamp: LocalDateTime? = null,

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,
)

@Embeddable
data class UserEmailId(
    @Column(name = "user_id")
    var userId: UUID? = null,

    @Column(name = "email")
    val email: String,
) : Serializable
