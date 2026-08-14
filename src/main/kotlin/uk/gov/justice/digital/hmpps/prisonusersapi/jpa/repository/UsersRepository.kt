package uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.User
import java.util.Optional
import java.util.UUID

@Repository
interface UsersRepository : JpaRepository<User, UUID> {
  fun existsUsersByLegacyStaffId(id: Long): Boolean

  @EntityGraph(attributePaths = ["userEmails"], type = EntityGraph.EntityGraphType.LOAD)
  fun findByLegacyStaffId(legacyStaffId: Long): Optional<User>
}
