package uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserCaseloadAdministrator
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserCaseloadAdministratorId

@Repository
interface UserCaseloadAdministratorRepository : JpaRepository<UserCaseloadAdministrator, UserCaseloadAdministratorId> {
  fun findAllByIdUsernameIn(usernames: Collection<String>): List<UserCaseloadAdministrator>
  fun deleteAllByIdUsernameIn(usernames: Collection<String>)
}
