package uk.gov.justice.digital.hmpps.prisonusersapi.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.NamedAttributeNode
import jakarta.persistence.NamedEntityGraph
import jakarta.persistence.NamedSubgraph
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.Hibernate
import uk.gov.justice.digital.hmpps.prisonusersapi.data.AccountStatus
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UsageType
import java.time.LocalDateTime

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
        NamedAttributeNode(value = "caseload"),
      ],
    ),
  ],
)
@NamedEntityGraph(
  name = "UserAccount.withUserAndActiveCaseload",
  attributeNodes = [
    NamedAttributeNode("username"),
    NamedAttributeNode("accountStatus"),
    NamedAttributeNode("activeCaseload"),
    NamedAttributeNode("user"),
  ],
)
@NamedEntityGraph(
  name = "UserAccount.withUserActiveCaseloadUserRoleCodes",
  attributeNodes = [
    NamedAttributeNode("username"),
    NamedAttributeNode("accountStatus"),
    NamedAttributeNode("activeCaseload"),
    NamedAttributeNode("user"),
    NamedAttributeNode("userRoleCodes"),
  ],
)
data class UserAccount(

  @Id
  @Column(name = "username", nullable = false)
  val username: String,

  @OneToOne
  @JoinColumn(name = "user_id")
  val user: User,

  @Enumerated(EnumType.STRING)
  val accountType: UsageType,

  @Convert(converter = AccountStatusConverter::class)
  val accountStatus: AccountStatus,

  @OneToOne(optional = true)
  @JoinColumn(name = "active_caseload_id")
  val activeCaseload: Caseload? = null,

  @OneToMany(mappedBy = "userAccount", cascade = [CascadeType.ALL], orphanRemoval = true)
  val userAccessibleCaseloads: MutableList<UserAccessibleCaseload> = mutableListOf(),

  @OneToMany(mappedBy = "userAccount", cascade = [CascadeType.ALL], orphanRemoval = true)
  val userRoleCodes: MutableList<UserRole> = mutableListOf(),

  val createdTimestamp: LocalDateTime,
  val createdBy: String,

  val lastLoggedIn: LocalDateTime? = null,

  val modifiedTimestamp: LocalDateTime? = null,
  val modifiedBy: String? = null,
) {

  fun isLocked(): Boolean = AccountStatus.entries.filter { it.isLocked }.contains(accountStatus)
  fun isAccountNonLocked(): Boolean = !isLocked()
  fun isEnabled(): Boolean = isAccountNonLocked()
  fun isAdmin(): Boolean = accountType == UsageType.ADMIN
  fun isCredentialsNonExpired(): Boolean = AccountStatus.entries.filterNot {
    it in setOf(
      AccountStatus.EXPIRED,
      AccountStatus.EXPIRED_LOCKED,
      AccountStatus.EXPIRED_LOCKED_TIMED,
    )
  }.contains(accountStatus)
  fun isActive(): Boolean = AccountStatus.entries.filter { !(it.isLocked || (it.isExpired && !it.isGracePeriod)) }.contains(accountStatus)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as UserAccount

    return username == other.username
  }

  override fun hashCode(): Int = username.hashCode()
}
