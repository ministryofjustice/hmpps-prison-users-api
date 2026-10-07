package uk.gov.justice.digital.hmpps.prisonusersapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PrisonUsersApi

fun main(args: Array<String>) {
  runApplication<PrisonUsersApi>(*args)
}
