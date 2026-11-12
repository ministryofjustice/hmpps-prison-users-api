package uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.SyncLock
import java.time.LocalDateTime

@Repository
interface SyncLockRepository : JpaRepository<SyncLock, Long> {
  @Modifying
  @Transactional(propagation = REQUIRES_NEW)
  @Query(
    value = "INSERT INTO sync_lock (legacy_staff_id, locked_at, locked_by) VALUES (:legacyStaffId, :lockedAt, :lockedBy)",
    nativeQuery = true,
  )
  fun acquire(legacyStaffId: Long, lockedAt: LocalDateTime, lockedBy: String)

  @Modifying
  @Transactional(propagation = REQUIRES_NEW)
  @Query("DELETE FROM SyncLock l WHERE l.legacyStaffId = :legacyStaffId AND l.lockedBy = :lockedBy")
  fun release(legacyStaffId: Long, lockedBy: String)
}
