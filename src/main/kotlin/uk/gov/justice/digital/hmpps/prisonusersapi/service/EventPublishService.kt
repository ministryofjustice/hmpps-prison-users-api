package uk.gov.justice.digital.hmpps.prisonusersapi.service

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
    snsService.publishDomainEvent(
      eventType = eventType,
      description = "${additionalInformation.userId} ${eventType.description}",
      occurredAt = OffsetDateTime.now(clock),
      additionalInformation = additionalInformation,
    )
  }
}