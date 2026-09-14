package uk.gov.justice.digital.hmpps.prisonusersapi.integration

import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.envers.AuditReaderFactory
import org.hibernate.envers.RevisionType
import org.hibernate.envers.query.AuditEntity
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonusersapi.integration.helper.DataBuilder
import uk.gov.justice.digital.hmpps.prisonusersapi.integration.helper.defaultUser
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.User
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UsersRepository
import java.time.LocalDateTime

class EnversIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var usersRepository: UsersRepository

  @Autowired
  private lateinit var dataBuilder: DataBuilder

  @Autowired
  private lateinit var entityManagerFactory: EntityManagerFactory

  @AfterEach
  fun tearDown() = dataBuilder.deleteAll()

  @Test
  fun `stores deleted entity data in the audit revision`() {
    val createdUser = usersRepository.saveAndFlush(
      defaultUser().copy(
        legacyStaffId = 900001L,
        firstName = "Created",
        lastName = "User",
      ),
    )

    val updatedUser = usersRepository.saveAndFlush(
      createdUser.copy(
        firstName = "Updated",
        modifiedBy = "TEST",
        modifiedTimestamp = LocalDateTime.now(),
      ),
    )

    usersRepository.delete(updatedUser)
    usersRepository.flush()

    val revisions = entityManagerFactory.createEntityManager().use { entityManager ->
      AuditReaderFactory.get(entityManager)
        .createQuery()
        .forRevisionsOfEntity(User::class.java, false, true)
        .add(AuditEntity.id().eq(createdUser.userId))
        .addOrder(AuditEntity.revisionNumber().asc())
        .resultList
    }

    assertThat(revisions).hasSize(3)

    val revisionTypes = revisions.map { (it as Array<*>)[2] as RevisionType }
    assertThat(revisionTypes).containsExactly(RevisionType.ADD, RevisionType.MOD, RevisionType.DEL)

    val deletedRevisionUser = (revisions[2] as Array<*>)[0] as User
    assertThat(deletedRevisionUser.firstName).isEqualTo("Updated")
    assertThat(deletedRevisionUser.lastName).isEqualTo("User")
    assertThat(deletedRevisionUser.legacyStaffId).isEqualTo(900001L)
  }
}
