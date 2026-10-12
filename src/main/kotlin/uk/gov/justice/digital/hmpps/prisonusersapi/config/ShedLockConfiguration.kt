package uk.gov.justice.digital.hmpps.prisonusersapi.config

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import java.sql.Connection

@Configuration
class ShedLockConfiguration {

  @Bean
  fun lockProvider(jdbcTemplate: JdbcTemplate, platformTransactionManager: PlatformTransactionManager): LockProvider = JdbcTemplateLockProvider(
    JdbcTemplateLockProvider.Configuration.builder()
      .withTableName("scheduled_job_lock")
      .withJdbcTemplate(jdbcTemplate)
      .withTransactionManager(platformTransactionManager)
      .withIsolationLevel(Connection.TRANSACTION_SERIALIZABLE)
      .usingDbTime()
      .build(),
  )
}
