package uk.gov.justice.digital.hmpps.prisonusersapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.OffsetDateTime

@Service
class EventPublishService(
  private val snsService: SnsService,
  private val clock: Clock,
) {

  fun publishEvent(
    eventType: PrisonUserDomainEventType,
    additionalInformation: AdditionalInformation,
  ) {
    try {
      snsService.publishDomainEvent(
        eventType = eventType,
        description = "${additionalInformation.username} ${eventType.description}",
        occurredAt = OffsetDateTime.now(clock),
        additionalInformation = additionalInformation,
      )
    } catch (e: Exception) {
      log.error("Failed to publish domain event $eventType for user ${additionalInformation.username}", e)
    }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(EventPublishService::class.java)
  }
}
