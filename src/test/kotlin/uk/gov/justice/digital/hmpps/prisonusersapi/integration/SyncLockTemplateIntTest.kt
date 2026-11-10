package uk.gov.justice.digital.hmpps.prisonusersapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.SyncLock
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.SyncLockRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.service.SyncLockAcquisitionTimeoutException
import uk.gov.justice.digital.hmpps.prisonusersapi.service.SyncLockTemplate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

@TestPropertySource(properties = ["sync.lock.max-wait-ms=100"])
class SyncLockTemplateIntTest(
  @Autowired private val syncLockTemplate: SyncLockTemplate,
  @Autowired private val syncLockRepository: SyncLockRepository,
  @Autowired transactionManager: PlatformTransactionManager,
) : IntegrationTestBase() {
  private val transactionTemplate = TransactionTemplate(transactionManager)
  private val legacyStaffId = 987654321L

  @AfterEach
  fun cleanUp() {
    syncLockRepository.deleteById(legacyStaffId)
  }

  @Test
  fun `holds lock until commit and prevents a concurrent operation from entering`() {
    val lockAcquired = CountDownLatch(1)
    val finishTransaction = CountDownLatch(1)
    val executor = Executors.newSingleThreadExecutor()
    try {
      val firstOperation = executor.submit {
        transactionTemplate.executeWithoutResult {
          syncLockTemplate.execute(legacyStaffId) { "first operation" }
          lockAcquired.countDown()
          check(finishTransaction.await(5, SECONDS))
        }
      }

      assertThat(lockAcquired.await(5, SECONDS)).isTrue()
      val owner = syncLockRepository.findById(legacyStaffId).orElseThrow().lockedBy

      assertThatThrownBy {
        syncLockTemplate.execute(legacyStaffId) { error("Concurrent operation entered") }
      }.isInstanceOf(SyncLockAcquisitionTimeoutException::class.java)

      assertThat(syncLockRepository.findById(legacyStaffId).orElseThrow().lockedBy).isEqualTo(owner)
      finishTransaction.countDown()
      firstOperation.get(5, SECONDS)

      assertThat(syncLockRepository.findById(legacyStaffId)).isEmpty()
      assertThat(syncLockTemplate.execute(legacyStaffId) { "next operation" }).isEqualTo("next operation")
    } finally {
      finishTransaction.countDown()
      executor.shutdownNow()
      check(executor.awaitTermination(5, SECONDS))
    }
  }

  @Test
  fun `releases lock after rollback`() {
    transactionTemplate.executeWithoutResult { transaction ->
      syncLockTemplate.execute(legacyStaffId) { "operation" }
      assertThat(syncLockRepository.findById(legacyStaffId)).isPresent()
      transaction.setRollbackOnly()
    }

    assertThat(syncLockRepository.findById(legacyStaffId)).isEmpty()
    assertThat(syncLockTemplate.execute(legacyStaffId) { "next operation" }).isEqualTo("next operation")
  }

  @Test
  fun `failed acquisition does not mark the caller transaction rollback only`() {
    syncLockRepository.saveAndFlush(
      SyncLock(legacyStaffId, LocalDateTime.now(), "another-operation"),
    )

    transactionTemplate.executeWithoutResult { transaction ->
      assertThatThrownBy {
        syncLockTemplate.execute(legacyStaffId) { error("Operation entered without lock") }
      }.isInstanceOf(SyncLockAcquisitionTimeoutException::class.java)

      assertThat(transaction.isRollbackOnly).isFalse()
    }

    assertThat(syncLockRepository.findById(legacyStaffId).orElseThrow().lockedBy).isEqualTo("another-operation")
  }

  @Test
  fun `releases lock when an operation outside a transaction fails`() {
    assertThatThrownBy {
      syncLockTemplate.execute(legacyStaffId) { throw IllegalStateException("Operation failed") }
    }.isInstanceOf(IllegalStateException::class.java)
      .hasMessage("Operation failed")

    assertThat(syncLockRepository.findById(legacyStaffId)).isEmpty()
  }

  @Test
  fun `release cannot delete another owner's lock`() {
    syncLockRepository.acquire(legacyStaffId, LocalDateTime.now(), "another-operation")

    syncLockRepository.release(legacyStaffId, "not-the-owner")

    assertThat(syncLockRepository.findById(legacyStaffId).orElseThrow().lockedBy).isEqualTo("another-operation")
  }
}
