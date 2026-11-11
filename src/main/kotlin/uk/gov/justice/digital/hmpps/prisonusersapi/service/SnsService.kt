package uk.gov.justice.digital.hmpps.prisonusersapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.publish
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class SnsService(
  hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(SnsService::class.java)
  }

  private val domaineventsTopic by lazy {
    hmppsQueueService.findByTopicId("domainevents")
      ?: throw RuntimeException("Topic with name domainevents doesn't exist")
  }

  @WithSpan(value = "hmpps-domain-events-topic", kind = SpanKind.PRODUCER)
  fun publishDomainEvent(
    eventType: PrisonUserDomainEventType,
    description: String,
    occurredAt: OffsetDateTime,
    additionalInformation: AdditionalInformation? = null,
  ) {
    publishToDomainEventsTopic(
      HMPPSDomainEvent(
        eventType = eventType.value,
        additionalInformation = additionalInformation,
        occurredAt = occurredAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        description = description,
      ),
    )
  }

  private fun publishToDomainEventsTopic(payload: HMPPSDomainEvent) {
    log.debug("Event {} for id {}", payload.eventType, payload.additionalInformation)
    domaineventsTopic.publish(
      eventType = payload.eventType.toString(),
      event = objectMapper.writeValueAsString(payload),
    ).also { log.info("Published event $payload to outbound topic") }
  }
}

data class AdditionalInformation(
  /** Internal ID */
  val userId: UUID,

  val legacyStaffId: Long,
  val username: String,
)

data class HMPPSDomainEvent(
  val eventType: String? = null,
  val additionalInformation: AdditionalInformation?,
  val version: Int = 1,
  val occurredAt: String,
  val description: String,
)

data class HMPPSMessageAttributes(
  val eventType: HMPPSEventType,
)

data class HMPPSMessage(
  @Suppress("PropertyName")
  val Message: String,
  @Suppress("PropertyName")
  val MessageAttributes: HMPPSMessageAttributes,
)

data class HMPPSEventType(
  @Suppress("PropertyName")
  val Value: String,
  @Suppress("PropertyName")
  val Type: String,
)

enum class PrisonUserDomainEventType(
  val value: String,
  val description: String,
) {
  USER_ACCOUNT_UNLOCKED(
    "prison-users.user.account.unlocked",
    "Prison user account has been unlocked",
  ),
}
