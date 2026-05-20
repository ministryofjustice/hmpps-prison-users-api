package uk.gov.justice.digital.hmpps.prisonusersapi.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.Hibernate

const val DPS_CASELOAD = "NWEB"

@Entity
@Table(name = "caseloads")
data class Caseload(
    @Id
    @Column(name = "caseload_id", nullable = false)
    val id: String,

    val name: String,
    val function: String,
) {
    fun isDpsCaseload(): Boolean = id == DPS_CASELOAD

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as Caseload

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}