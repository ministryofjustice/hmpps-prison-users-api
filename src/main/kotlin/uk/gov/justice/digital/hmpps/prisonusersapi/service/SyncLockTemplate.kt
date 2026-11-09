package uk.gov.justice.digital.hmpps.prisonusersapi.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.SyncLock
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.SyncLockRepository

@Component
class SyncLockTemplate(
  private val syncLockRepository: SyncLockRepository,
  @Value($$"${sync.lock.retry-backoff-ms:50}") private val lockRetryBackoffMs: Long,
  @Value($$"${sync.lock.max-wait-ms:5000}") private val lockMaxWaitMs: Long,
) {

  fun <T> execute(legacyStaffId: Long, block: () -> T): T {
    val startTime = System.currentTimeMillis()
    while (true) {
      try {
        acquire(legacyStaffId)
        return block()
      } catch (e: SyncLockBusyException) {
        if (System.currentTimeMillis() - startTime > lockMaxWaitMs) {
          throw SyncLockAcquisitionTimeoutException("Timeout waiting for sync lock for legacy staff id $legacyStaffId", e)
        }
        pauseFor(legacyStaffId)
      } finally {
        release()
      }
    }
  }

  private fun pauseFor(legacyStaffId: Long) {
    try {
      Thread.sleep(lockRetryBackoffMs)
    } catch (interrupted: InterruptedException) {
      Thread.currentThread().interrupt()
      throw SyncLockAcquisitionTimeoutException(
        "Interrupted while waiting to acquire sync lock for legacy staff id $legacyStaffId",
        interrupted,
      )
    }
  }

  private fun acquire(legacyStaffId: Long) {
    try {
      syncLockRepository.saveAndFlush(
        SyncLock(
          legacyStaffId = legacyStaffId,
          lockedAt = java.time.LocalDateTime.now(),
          lockedBy = Thread.currentThread().name,
        ),
      )
    } catch (e: DataIntegrityViolationException) {
      throw SyncLockBusyException("Sync lock is already held for legacy staff id $legacyStaffId", e)
    }
  }

  private fun release() {
    syncLockRepository.deleteByLockedBy(Thread.currentThread().name)
  }
}

class SyncLockAcquisitionTimeoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class SyncLockBusyException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
