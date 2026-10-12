package uk.gov.justice.digital.hmpps.prisonusersapi

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M", defaultLockAtLeastFor = "PT3M")
class PrisonUsersApi

fun main(args: Array<String>) {
  runApplication<PrisonUsersApi>(*args)
}
