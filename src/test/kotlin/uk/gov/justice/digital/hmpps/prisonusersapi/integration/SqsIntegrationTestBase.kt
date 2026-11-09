package uk.gov.justice.digital.hmpps.prisonusersapi.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import uk.gov.justice.digital.hmpps.prisonusersapi.config.LocalStackTestContainer
import uk.gov.justice.digital.hmpps.prisonusersapi.service.HMPPSDomainEvent
import uk.gov.justice.digital.hmpps.prisonusersapi.service.HMPPSMessage
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.Clock

@AutoConfigureJson
abstract class SqsIntegrationTestBase : IntegrationTestBase() {

  @MockitoBean
  private lateinit var clock: Clock

  @BeforeEach
  fun setupClock() {
    whenever(clock.instant()).thenReturn(IntegrationTestBase.clock.instant())
    whenever(clock.zone).thenReturn(IntegrationTestBase.clock.zone)
  }

  companion object {
    private val localstackInstance = LocalStackTestContainer.instance

    @Suppress("unused")
    @JvmStatic
    @DynamicPropertySource
    fun localstackProperties(registry: DynamicPropertyRegistry) {
      localstackInstance?.let { LocalStackTestContainer.setupProperties(localstackInstance, registry) }
    }
  }

  init {
    // Resolves an issue where Wiremock keeps previous sockets open from other tests causing connection resets
    System.setProperty("http.keepAlive", "false")
  }

  @Autowired
  protected lateinit var objectMapper: ObjectMapper

  @Autowired
  private lateinit var hmppsQueueService: HmppsQueueService

  protected val testDomainEventQueue by lazy { hmppsQueueService.findByQueueId("test") as HmppsQueue }

  @BeforeEach
  fun cleanQueue() {
    testDomainEventQueue.sqsClient.purgeQueue(
      PurgeQueueRequest.builder().queueUrl(testDomainEventQueue.queueUrl).build(),
    ).get()
    testDomainEventQueue.sqsClient.countMessagesOnQueue(testDomainEventQueue.queueUrl).get()
  }

  fun assertThatDomainEventWasSent(
    eventType: String,
    legacyStaffId: Long,
    username: String,
  ) {
    getDomainEvents(1).let {
      val event = it[0]
      assertThat(event.eventType).isEqualTo(eventType)
      assertThat(event.additionalInformation?.legacyStaffId).isEqualTo(legacyStaffId)
      assertThat(event.additionalInformation?.username).isEqualTo(username)
    }
  }

  fun getDomainEvents(messageCount: Int = 1): List<HMPPSDomainEvent> {
    val sqsClient = testDomainEventQueue.sqsClient

    val messages: MutableList<HMPPSDomainEvent> = mutableListOf()
    await untilCallTo {
      messages.addAll(
        sqsClient.receiveMessage(ReceiveMessageRequest.builder().queueUrl(testDomainEventQueue.queueUrl).build())
          .get()
          .messages()
          .map { objectMapper.readValue(it.body(), HMPPSMessage::class.java) }
          .map { objectMapper.readValue(it.Message, HMPPSDomainEvent::class.java) },
      )
    } matches { messages.size == messageCount }

    return messages
  }
}