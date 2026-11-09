package uk.gov.justice.digital.hmpps.prisonusersapi.integration

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@TestConfiguration
class IntegrationTestConfig {

  @Primary
  fun clock(): Clock = Clock.fixed(
    Instant.parse("2023-12-05T12:34:56.123456+00:00"),
    ZoneId.of("Europe/London"),
  )
}
