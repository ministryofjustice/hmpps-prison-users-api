package uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.User
import java.util.Optional
import java.util.UUID

@Repository
interface UsersRepository : JpaRepository<User, UUID> {
  fun existsUsersByLegacyStaffId(id: Long): Boolean

  @EntityGraph(attributePaths = ["userEmails"], type = EntityGraph.EntityGraphType.LOAD)
  fun findByLegacyStaffId(legacyStaffId: Long): Optional<User>

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = ["userEmails"], type = EntityGraph.EntityGraphType.LOAD)
  @Query("select u from User u where u.legacyStaffId = :legacyStaffId")
  fun findByLegacyStaffIdForUpdate(@Param("legacyStaffId") legacyStaffId: Long): Optional<User>
}
