package uk.gov.justice.digital.hmpps.prisonusersapi.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "sync_lock")
data class SyncLock(
  @Id
  @Column(name = "legacy_staff_id", nullable = false)
  val legacyStaffId: Long,

  @Column(name = "locked_at", nullable = false)
  val lockedAt: LocalDateTime,

  @Column(name = "locked_by", nullable = false)
  val lockedBy: String,
)
