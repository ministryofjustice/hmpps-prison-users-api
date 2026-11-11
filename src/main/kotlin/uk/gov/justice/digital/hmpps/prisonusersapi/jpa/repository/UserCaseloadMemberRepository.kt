package uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserCaseloadMember
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserCaseloadMemberId

@Repository
interface UserCaseloadMemberRepository : JpaRepository<UserCaseloadMember, UserCaseloadMemberId> {
  fun findAllByIdUsernameIn(usernames: Collection<String>): List<UserCaseloadMember>
  fun deleteAllByIdUsernameIn(usernames: Collection<String>)

  @Query(
    """
    SELECT EXISTS (
      SELECT 1
      FROM UserCaseloadMember caseloadMember, UserCaseloadAdministrator caseloadAdministrator
      WHERE caseloadMember.id.caseloadId = caseloadAdministrator.id.caseloadId
      AND caseloadMember.id.username = :username
      AND caseloadMember.active = true
      AND caseloadAdministrator.id.username = :localAdministratorUsername
      AND caseloadAdministrator.active = true
    )
    """,
  )
  fun isAdministeredBy(
    @Param("username") username: String,
    @Param("localAdministratorUsername") localAdministratorUsername: String,
  ): Boolean
}
