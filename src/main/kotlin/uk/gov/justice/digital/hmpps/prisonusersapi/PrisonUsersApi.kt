package uk.gov.justice.digital.hmpps.prisonusersapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PrisonUsersApi

fun main(args: Array<String>) {
  runApplication<PrisonUsersApi>(*args)
}
