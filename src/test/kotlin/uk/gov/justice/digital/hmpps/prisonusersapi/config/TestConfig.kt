package uk.gov.justice.digital.hmpps.prisonusersapi.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset

@TestConfiguration
class TestConfig {
  @Bean
  @Primary
  fun fixedClock(): Clock = Clock.fixed(FIXED_TIME.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

  companion object {
    val FIXED_TIME: LocalDateTime = LocalDateTime.of(2025, 1, 15, 12, 0)
  }
}
