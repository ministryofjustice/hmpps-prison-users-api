package uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserCaseloadMember
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserCaseloadMemberId

@Repository
interface UserCaseloadMemberRepository : JpaRepository<UserCaseloadMember, UserCaseloadMemberId> {
  fun findAllByIdUsernameIn(usernames: Collection<String>): List<UserCaseloadMember>
  fun deleteAllByIdUsernameIn(usernames: Collection<String>)
}
