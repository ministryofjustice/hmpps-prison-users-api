package uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccount
import java.util.Optional

@Repository
interface UserAccountRepository : JpaRepository<UserAccount, String> {

  @EntityGraph(value = "UserAccount.caseloads", type = EntityGraph.EntityGraphType.LOAD)
  override fun findById(id: String?): Optional<UserAccount?>
}
