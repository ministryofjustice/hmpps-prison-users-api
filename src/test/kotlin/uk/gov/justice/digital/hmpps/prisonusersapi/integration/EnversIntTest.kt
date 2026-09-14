package uk.gov.justice.digital.hmpps.prisonusersapi.integration

import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.envers.AuditReaderFactory
import org.hibernate.envers.RevisionType
import org.hibernate.envers.query.AuditEntity
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonusersapi.integration.helper.DataBuilder
import uk.gov.justice.digital.hmpps.prisonusersapi.integration.helper.defaultUser
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.User
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccount
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserCaseloadAdministrator
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserCaseloadAdministratorId
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserCaseloadMember
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserCaseloadMemberId
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserAccountRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UsersRepository
import java.time.LocalDate
import java.time.LocalDateTime

class EnversIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var usersRepository: UsersRepository

  @Autowired
  private lateinit var userAccountRepository: UserAccountRepository

  @Autowired
  private lateinit var dataBuilder: DataBuilder

  @Autowired
  private lateinit var entityManagerFactory: EntityManagerFactory

  @AfterEach
  fun tearDown() = dataBuilder.deleteAll()

  @Nested
  inner class UserAuditing {

    @Test
    fun `captures create update and delete revisions for user`() {
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

      val revisions = auditRevisions(User::class.java, createdUser.userId!!)

      assertRevisionTypes(revisions)
      assertThat(revisions.map { it.entity.firstName }).containsExactly("Created", "Updated", "Updated")
      assertThat(revisions.map { it.entity.lastName }).containsExactly("User", "User", "User")
      assertThat(revisions.map { it.entity.legacyStaffId }).containsExactly(900001L, 900001L, 900001L)
    }
  }

  @Nested
  inner class UserAccountAuditing {

    @Test
    fun `captures create update and delete revisions for user account`() {
      val createdAccount = dataBuilder.generalUser()
        .username("AUDIT_ACCOUNT")
        .atPrison("MDI")
        .buildAndSave()

      val updatedAccount = userAccountRepository.saveAndFlush(
        createdAccount.copy(
          lastLoggedIn = LocalDateTime.of(2026, 1, 15, 9, 30),
          modifiedBy = "TEST",
          modifiedTimestamp = LocalDateTime.now(),
        ),
      )

      userAccountRepository.delete(updatedAccount)
      userAccountRepository.flush()

      val revisions = auditRevisions(UserAccount::class.java, createdAccount.username)

      assertRevisionTypes(revisions)
      assertThat(revisions.map { it.entity.username }).containsExactly("AUDIT_ACCOUNT", "AUDIT_ACCOUNT", "AUDIT_ACCOUNT")
      assertThat(revisions.map { it.entity.lastLoggedIn }).containsExactly(null, LocalDateTime.of(2026, 1, 15, 9, 30), LocalDateTime.of(2026, 1, 15, 9, 30))
      assertThat(revisions.map { it.entity.createdBy }).containsExactly("TEST", "TEST", "TEST")
    }
  }

  @Nested
  inner class UserCaseloadMemberAuditing {

    @Test
    fun `captures create update and delete revisions for user caseload member`() {
      val account = dataBuilder.generalUser()
        .username("AUDIT_MEMBER")
        .atPrison("LEI")
        .buildAndSave()
      val memberId = UserCaseloadMemberId(username = account.username, caseloadId = "LEI")
      val createdTimestamp = LocalDateTime.of(2026, 2, 1, 8, 0)
      val expiryDate = LocalDate.of(2026, 12, 31)

      withTransaction { entityManager ->
        val managedAccount = entityManager.find(UserAccount::class.java, account.username)
        val managedCaseload = entityManager.find(uk.gov.justice.digital.hmpps.prisonusersapi.jpa.Caseload::class.java, "LEI")

        entityManager.persist(
          UserCaseloadMember(
            id = memberId,
            caseload = managedCaseload,
            userAccount = managedAccount,
            startDate = LocalDate.of(2026, 2, 1),
            active = true,
            createdBy = "TEST",
            createdTimestamp = createdTimestamp,
          ),
        )
      }

      withTransaction { entityManager ->
        val managedMember = entityManager.find(UserCaseloadMember::class.java, memberId)
        entityManager.merge(
          managedMember.copy(
            expiryDate = expiryDate,
            active = false,
            modifiedBy = "TEST",
            modifiedTimestamp = LocalDateTime.of(2026, 2, 10, 8, 0),
          ),
        )
      }

      withTransaction { entityManager ->
        entityManager.remove(entityManager.find(UserCaseloadMember::class.java, memberId))
      }

      val revisions = auditRevisions(UserCaseloadMember::class.java, memberId)

      assertRevisionTypes(revisions)
      assertThat(revisions.map { it.entity.startDate }).containsExactly(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1))
      assertThat(revisions.map { it.entity.active }).containsExactly(true, false, false)
      assertThat(revisions.map { it.entity.expiryDate }).containsExactly(null, expiryDate, expiryDate)
    }
  }

  @Nested
  inner class UserCaseloadAdministratorAuditing {

    @Test
    fun `captures create update and delete revisions for user caseload administrator`() {
      val account = dataBuilder.generalUser()
        .username("AUDIT_ADMIN")
        .atPrison("WWI")
        .buildAndSave()
      val administratorId = UserCaseloadAdministratorId(username = account.username, caseloadId = "WWI")
      val expiryDate = LocalDate.of(2026, 11, 30)

      withTransaction { entityManager ->
        val managedAccount = entityManager.find(UserAccount::class.java, account.username)
        val managedCaseload = entityManager.find(uk.gov.justice.digital.hmpps.prisonusersapi.jpa.Caseload::class.java, "WWI")

        entityManager.persist(
          UserCaseloadAdministrator(
            id = administratorId,
            caseload = managedCaseload,
            userAccount = managedAccount,
            active = true,
            createdBy = "TEST",
            createdTimestamp = LocalDateTime.of(2026, 3, 1, 8, 0),
          ),
        )
      }

      withTransaction { entityManager ->
        val managedAdministrator = entityManager.find(UserCaseloadAdministrator::class.java, administratorId)
        entityManager.merge(
          managedAdministrator.copy(
            active = false,
            expiryDate = expiryDate,
            modifiedBy = "TEST",
            modifiedTimestamp = LocalDateTime.of(2026, 3, 5, 8, 0),
          ),
        )
      }

      withTransaction { entityManager ->
        entityManager.remove(entityManager.find(UserCaseloadAdministrator::class.java, administratorId))
      }

      val revisions = auditRevisions(UserCaseloadAdministrator::class.java, administratorId)

      assertRevisionTypes(revisions)
      assertThat(revisions.map { it.entity.active }).containsExactly(true, false, false)
      assertThat(revisions.map { it.entity.expiryDate }).containsExactly(null, expiryDate, expiryDate)
      assertThat(revisions.map { it.entity.createdBy }).containsExactly("TEST", "TEST", "TEST")
    }
  }

  private fun <T : Any, ID : Any> auditRevisions(entityClass: Class<T>, id: ID): List<AuditRevision<T>> = entityManagerFactory.createEntityManager().use { entityManager ->
    @Suppress("UNCHECKED_CAST")
    AuditReaderFactory.get(entityManager)
      .createQuery()
      .forRevisionsOfEntity(entityClass, false, true)
      .add(AuditEntity.id().eq(id))
      .addOrder(AuditEntity.revisionNumber().asc())
      .resultList
      .map { revision ->
        val revisionData = revision as Array<*>
        AuditRevision(
          entity = revisionData[0] as T,
          revisionType = revisionData[2] as RevisionType,
        )
      }
  }

  private fun <T> withTransaction(action: (EntityManager) -> T): T = entityManagerFactory.createEntityManager().use { entityManager ->
    entityManager.transaction.begin()
    try {
      val result = action(entityManager)
      entityManager.flush()
      entityManager.transaction.commit()
      result
    } catch (e: Exception) {
      if (entityManager.transaction.isActive) {
        entityManager.transaction.rollback()
      }
      throw e
    }
  }

  private fun <T> assertRevisionTypes(revisions: List<AuditRevision<T>>) {
    assertThat(revisions.size).isEqualTo(3)
    assertThat(revisions.map { it.revisionType }).containsExactly(RevisionType.ADD, RevisionType.MOD, RevisionType.DEL)
  }

  private class AuditRevision<T>(
    val entity: T,
    val revisionType: RevisionType,
  )
}
