package uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccount
import java.util.Optional
import java.util.UUID

@Repository
interface UserAccountRepository : JpaRepository<UserAccount, String> {

  @Query("SELECT a.user.legacyStaffId FROM UserAccount a WHERE a.username = :username")
  fun findLegacyStaffIdByUsername(username: String): Optional<Long>

  @EntityGraph(value = "UserAccount.caseloads", type = EntityGraph.EntityGraphType.LOAD)
  override fun findById(id: String): Optional<UserAccount>

  @EntityGraph(value = "UserAccount.withUserActiveCaseloadUserRoleCodes", type = EntityGraph.EntityGraphType.LOAD)
  fun findWithUserAndActiveCaseloadAndUserRoleCodesByUsername(username: String): Optional<UserAccount>

  @EntityGraph(value = "UserAccount.withUserAndActiveCaseload", type = EntityGraph.EntityGraphType.LOAD)
  fun findWithUserAndActiveCaseloadByUsername(username: String): Optional<UserAccount>

  @EntityGraph(value = "UserAccount.withUserAndActiveCaseload", type = EntityGraph.EntityGraphType.LOAD)
  fun findWithUserAndActiveCaseloadByUsernameIn(usernames: List<String>): List<UserAccount>

  @EntityGraph(value = "UserAccount.withCaseloads", type = EntityGraph.EntityGraphType.LOAD)
  fun findAllByUserUserId(userId: UUID): List<UserAccount>

  fun existsByUsername(username: String): Boolean
}
