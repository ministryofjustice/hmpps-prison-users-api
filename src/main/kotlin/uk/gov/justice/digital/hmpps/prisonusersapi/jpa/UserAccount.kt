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

@Entity
@Table(name = "user_account")
@NamedEntityGraph(
    name = "UserAccount.withCaseloads",
    attributeNodes = [
        NamedAttributeNode("username"),
        NamedAttributeNode("accountType"),
        NamedAttributeNode("accountStatus"),
        NamedAttributeNode("activeCaseload"),
        NamedAttributeNode(value = "userAccessibleCaseloads", subgraph = "UserAccount.accessibleCaseloads"),
    ],
    subgraphs = [
        NamedSubgraph(
            name = "UserAccount.accessibleCaseloads",
            attributeNodes = [
                NamedAttributeNode(value = "caseload")
            ],
        ),
    ],
)
data class UserAccount(

    @Id
    @Column(name = "username", nullable = false)
    val username: String,

    @Enumerated(EnumType.STRING)
    val accountType: UsageType,

    @Convert(converter = AccountStatusConverter::class)
    val accountStatus: AccountStatus,

    @OneToOne(optional = true)
    val activeCaseload: Caseload? = null,

    @OneToMany(mappedBy = "username", cascade = [CascadeType.ALL], orphanRemoval = true)
    val userAccessibleCaseloads: MutableList<UserAccessibleCaseload> = mutableListOf(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as UserAccount

        return username == other.username
    }

    override fun hashCode(): Int = username.hashCode()
}