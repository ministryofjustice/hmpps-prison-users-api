package uk.gov.justice.digital.hmpps.prisonusersapi.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.SyncLockRepository
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Component
class SyncLockTemplate(
  private val syncLockRepository: SyncLockRepository,
  private val clock: Clock,
  @Value($$"${sync.lock.retry-backoff-ms:50}") private val lockRetryBackoffMs: Long,
  @Value($$"${sync.lock.max-wait-ms:5000}") private val lockMaxWaitMs: Long,
) {

  fun <T> execute(legacyStaffId: Long, block: () -> T): T {
    val startTime = System.nanoTime()
    val lockedBy = UUID.randomUUID().toString()
    while (true) {
      try {
        acquire(legacyStaffId, lockedBy)
        break
      } catch (e: SyncLockBusyException) {
        if ((System.nanoTime() - startTime) / 1_000_000 >= lockMaxWaitMs) {
          throw SyncLockAcquisitionTimeoutException("Timeout waiting for sync lock for legacy staff id $legacyStaffId", e)
        }
        pauseFor(legacyStaffId)
      }
    }

    val releaseAfterTransaction = TransactionSynchronizationManager.isSynchronizationActive()
    if (releaseAfterTransaction) {
      // Keep the lock until the protected changes have committed or rolled back.
      TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
        override fun afterCompletion(status: Int) {
          release(legacyStaffId, lockedBy)
        }
      })
    }

    try {
      return block()
    } finally {
      if (!releaseAfterTransaction) {
        release(legacyStaffId, lockedBy)
      }
    }
  }

  private fun release(legacyStaffId: Long, lockedBy: String) {
    try {
      syncLockRepository.release(legacyStaffId, lockedBy)
    } catch (e: Exception) {
      log.error("Failed to release lock for legacy staff id $legacyStaffId", e)
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

  private fun acquire(legacyStaffId: Long, lockedBy: String) {
    try {
      syncLockRepository.acquire(legacyStaffId, LocalDateTime.now(clock), lockedBy)
    } catch (e: DataIntegrityViolationException) {
      throw SyncLockBusyException("Sync lock is already held for legacy staff id $legacyStaffId", e)
    }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(SyncLockTemplate::class.java)
  }
}

class SyncLockAcquisitionTimeoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class SyncLockBusyException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
