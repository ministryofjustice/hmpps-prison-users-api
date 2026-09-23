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
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.Caseload
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.User
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccessibleCaseload
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccessibleCaseloadId
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccount
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserCaseloadAdministrator
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserCaseloadAdministratorId
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserCaseloadMember
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserCaseloadMemberId
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserEmail
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserRole
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserRoleId
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserAccountRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UsersRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

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
  inner class RepositoryBackedAuditing {

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

        val revisions = saveUpdateDelete(
          entity = createdUser,
          update = {
            copy(
              firstName = "Updated",
              modifiedBy = "TEST",
              modifiedTimestamp = LocalDateTime.now(),
            )
          },
          save = usersRepository::saveAndFlush,
          delete = {
            usersRepository.delete(it)
            usersRepository.flush()
          },
          entityClass = User::class.java,
          auditId = createdUser.userId!!,
        )

        assertThat(revisions.map { it.entity.firstName }).containsExactly("Created", "Updated", "Updated")
        assertThat(revisions.map { it.entity.lastName }).containsExactly("User", "User", "User")
        assertThat(revisions.map { it.entity.legacyStaffId }).containsExactly(900001L, 900001L, 900001L)
      }

      @Test
      fun `captures delete revisions for linked user emails when user is deleted`() {
        val createdUser = defaultUser().copy(
          legacyStaffId = 900002L,
          firstName = "Created",
          lastName = "User",
          userEmails = mutableListOf(),
        ).also { user ->
          user.addUserEmail(
            UserEmail(
              email = "audit.user.one@example.org",
              isPrimary = true,
              createdBy = "TEST",
              createdTimestamp = LocalDateTime.of(2026, 7, 1, 8, 0),
              user = user,
            ),
          )
          user.addUserEmail(
            UserEmail(
              email = "audit.user.two@example.org",
              isPrimary = false,
              createdBy = "TEST",
              createdTimestamp = LocalDateTime.of(2026, 7, 1, 8, 5),
              user = user,
            ),
          )
        }

        val savedUser = usersRepository.saveAndFlush(createdUser)
        val emailIds = savedUser.userEmails.map { requireNotNull(it.id) }

        usersRepository.delete(savedUser)
        usersRepository.flush()

        val expectedEmails = listOf("audit.user.one@example.org", "audit.user.two@example.org")
        emailIds.zip(expectedEmails).forEach { (emailId, expectedEmail) ->
          val revisions = auditRevisions(UserEmail::class.java, emailId)

          assertThat(revisions.map { it.revisionType }).containsExactly(RevisionType.ADD, RevisionType.DEL)
          assertThat(revisions.map { it.entity.email }).containsExactly(expectedEmail, expectedEmail)
          assertThat(revisions.map { it.entity.isPrimary }).containsExactly(expectedEmail == "audit.user.one@example.org", expectedEmail == "audit.user.one@example.org")
        }
      }
    }

    @Nested
    inner class UserAccountAuditing {

      @Test
      fun `captures create update and delete revisions for user account`() {
        val createdAccount = createGeneralAccount(username = "AUDIT_ACCOUNT", caseloadId = "MDI")
        val expectedLastLoggedIn = LocalDateTime.of(2026, 1, 15, 9, 30)

        val revisions = saveUpdateDelete(
          entity = createdAccount,
          update = {
            copy(
              lastLoggedIn = expectedLastLoggedIn,
              modifiedBy = "TEST",
              modifiedTimestamp = LocalDateTime.now(),
            )
          },
          save = userAccountRepository::saveAndFlush,
          delete = {
            userAccountRepository.delete(it)
            userAccountRepository.flush()
          },
          entityClass = UserAccount::class.java,
          auditId = createdAccount.username,
        )

        assertThat(revisions.map { it.entity.username }).containsExactly("AUDIT_ACCOUNT", "AUDIT_ACCOUNT", "AUDIT_ACCOUNT")
        assertThat(revisions.map { it.entity.lastLoggedIn }).containsExactly(null, expectedLastLoggedIn, expectedLastLoggedIn)
        assertThat(revisions.map { it.entity.createdBy }).containsExactly("TEST", "TEST", "TEST")
      }
    }
  }

  @Nested
  inner class ChildEntityAuditing {

    @Nested
    inner class UserEmailAuditing {

      @Test
      fun `captures create update and delete revisions for user email`() {
        val account = createGeneralAccount(username = "AUDIT_EMAIL", caseloadId = "LEI")
        val createdTimestamp = LocalDateTime.of(2026, 4, 1, 8, 0)
        var emailId: Long? = null

        val revisions = persistUpdateDelete(
          create = { entityManager ->
            val userEmail = UserEmail(
              email = "audit.email@example.org",
              isPrimary = false,
              createdBy = "TEST",
              createdTimestamp = createdTimestamp,
              user = managedUser(entityManager, account.user.userId!!),
            )

            entityManager.persist(userEmail)
            entityManager.flush()
            userEmail.id!!.also { emailId = it }
          },
          update = { entityManager ->
            entityManager.merge(
              entityManager.find(UserEmail::class.java, emailId!!).copy(
                email = "audit.email@justice.gov.uk",
                isPrimary = true,
                modifiedBy = "TEST",
                modifiedTimestamp = LocalDateTime.of(2026, 4, 2, 8, 0),
              ),
            )
          },
          delete = { entityManager ->
            entityManager.remove(entityManager.find(UserEmail::class.java, emailId!!))
          },
          entityClass = UserEmail::class.java,
        )

        assertThat(revisions.map { it.entity.email }).containsExactly("audit.email@example.org", "audit.email@justice.gov.uk", "audit.email@justice.gov.uk")
        assertThat(revisions.map { it.entity.isPrimary }).containsExactly(false, true, true)
        assertThat(revisions.map { it.entity.createdBy }).containsExactly("TEST", "TEST", "TEST")
      }
    }

    @Nested
    inner class CompositeKeyAuditing {

      @Test
      fun `captures create update and delete revisions for user role`() {
        val account = createGeneralAccount(username = "AUDIT_ROLE", caseloadId = "WWI")
        val roleId = UserRoleId(username = account.username, roleCode = "ROLE_AUDIT")
        val createdTimestamp = LocalDateTime.of(2026, 5, 1, 8, 0)
        val updatedTimestamp = LocalDateTime.of(2026, 5, 2, 8, 0)

        val revisions = persistUpdateDelete(
          create = { entityManager ->
            entityManager.persist(
              UserRole(
                id = roleId,
                userAccount = managedUserAccount(entityManager, account.username),
                createdBy = "TEST",
                createdTimestamp = createdTimestamp,
              ),
            )
            roleId
          },
          update = { entityManager ->
            entityManager.merge(
              entityManager.find(UserRole::class.java, roleId).copy(
                createdBy = "UPDATED_TEST",
                createdTimestamp = updatedTimestamp,
              ),
            )
          },
          delete = { entityManager ->
            entityManager.remove(entityManager.find(UserRole::class.java, roleId))
          },
          entityClass = UserRole::class.java,
        )

        assertThat(revisions.map { it.entity.id.roleCode }).containsExactly("ROLE_AUDIT", "ROLE_AUDIT", "ROLE_AUDIT")
        assertThat(revisions.map { it.entity.createdBy }).containsExactly("TEST", "UPDATED_TEST", "UPDATED_TEST")
        assertThat(revisions.map { it.entity.createdTimestamp }).containsExactly(createdTimestamp, updatedTimestamp, updatedTimestamp)
      }

      @Test
      fun `captures create update and delete revisions for user accessible caseload`() {
        val account = createGeneralAccount(username = "AUDIT_ACCESSIBLE", caseloadId = "WWI")
        val accessibleCaseloadId = UserAccessibleCaseloadId(username = account.username, caseloadId = "MDI")
        val createdTimestamp = LocalDateTime.of(2026, 6, 1, 8, 0)
        val updatedTimestamp = LocalDateTime.of(2026, 6, 2, 8, 0)

        val revisions = persistUpdateDelete(
          create = { entityManager ->
            entityManager.persist(
              UserAccessibleCaseload(
                id = accessibleCaseloadId,
                caseload = managedCaseload(entityManager, "MDI"),
                userAccount = managedUserAccount(entityManager, account.username),
                createdBy = "TEST",
                createdTimestamp = createdTimestamp,
              ),
            )
            accessibleCaseloadId
          },
          update = { entityManager ->
            entityManager.merge(
              entityManager.find(UserAccessibleCaseload::class.java, accessibleCaseloadId).copy(
                createdBy = "UPDATED_TEST",
                createdTimestamp = updatedTimestamp,
              ),
            )
          },
          delete = { entityManager ->
            entityManager.remove(entityManager.find(UserAccessibleCaseload::class.java, accessibleCaseloadId))
          },
          entityClass = UserAccessibleCaseload::class.java,
        )

        assertThat(revisions.map { it.entity.id.caseloadId }).containsExactly("MDI", "MDI", "MDI")
        assertThat(revisions.map { it.entity.createdBy }).containsExactly("TEST", "UPDATED_TEST", "UPDATED_TEST")
        assertThat(revisions.map { it.entity.createdTimestamp }).containsExactly(createdTimestamp, updatedTimestamp, updatedTimestamp)
      }

      @Test
      fun `captures create update and delete revisions for user caseload member`() {
        val account = createGeneralAccount(username = "AUDIT_MEMBER", caseloadId = "LEI")
        val memberId = UserCaseloadMemberId(username = account.username, caseloadId = "LEI")
        val startDate = LocalDate.of(2026, 2, 1)
        val createdTimestamp = LocalDateTime.of(2026, 2, 1, 8, 0)
        val expiryDate = LocalDate.of(2026, 12, 31)

        val revisions = persistUpdateDelete(
          create = { entityManager ->
            entityManager.persist(
              UserCaseloadMember(
                id = memberId,
                caseload = managedCaseload(entityManager, "LEI"),
                userAccount = managedUserAccount(entityManager, account.username),
                startDate = startDate,
                active = true,
                createdBy = "TEST",
                createdTimestamp = createdTimestamp,
              ),
            )
            memberId
          },
          update = { entityManager ->
            entityManager.merge(
              entityManager.find(UserCaseloadMember::class.java, memberId).copy(
                expiryDate = expiryDate,
                active = false,
                modifiedBy = "TEST",
                modifiedTimestamp = LocalDateTime.of(2026, 2, 10, 8, 0),
              ),
            )
          },
          delete = { entityManager ->
            entityManager.remove(entityManager.find(UserCaseloadMember::class.java, memberId))
          },
          entityClass = UserCaseloadMember::class.java,
        )

        assertThat(revisions.map { it.entity.startDate }).containsExactly(startDate, startDate, startDate)
        assertThat(revisions.map { it.entity.active }).containsExactly(true, false, false)
        assertThat(revisions.map { it.entity.expiryDate }).containsExactly(null, expiryDate, expiryDate)
      }

      @Test
      fun `captures create update and delete revisions for user caseload administrator`() {
        val account = createGeneralAccount(username = "AUDIT_ADMIN", caseloadId = "WWI")
        val administratorId = UserCaseloadAdministratorId(username = account.username, caseloadId = "WWI")
        val expiryDate = LocalDate.of(2026, 11, 30)

        val revisions = persistUpdateDelete(
          create = { entityManager ->
            entityManager.persist(
              UserCaseloadAdministrator(
                id = administratorId,
                caseload = managedCaseload(entityManager, "WWI"),
                userAccount = managedUserAccount(entityManager, account.username),
                active = true,
                createdBy = "TEST",
                createdTimestamp = LocalDateTime.of(2026, 3, 1, 8, 0),
              ),
            )
            administratorId
          },
          update = { entityManager ->
            entityManager.merge(
              entityManager.find(UserCaseloadAdministrator::class.java, administratorId).copy(
                active = false,
                expiryDate = expiryDate,
                modifiedBy = "TEST",
                modifiedTimestamp = LocalDateTime.of(2026, 3, 5, 8, 0),
              ),
            )
          },
          delete = { entityManager ->
            entityManager.remove(entityManager.find(UserCaseloadAdministrator::class.java, administratorId))
          },
          entityClass = UserCaseloadAdministrator::class.java,
        )

        assertThat(revisions.map { it.entity.active }).containsExactly(true, false, false)
        assertThat(revisions.map { it.entity.expiryDate }).containsExactly(null, expiryDate, expiryDate)
        assertThat(revisions.map { it.entity.createdBy }).containsExactly("TEST", "TEST", "TEST")
      }
    }
  }

  private fun createGeneralAccount(username: String, caseloadId: String): UserAccount = dataBuilder.generalUser()
    .username(username)
    .atPrison(caseloadId)
    .buildAndSave()

  private fun managedUser(entityManager: EntityManager, userId: UUID): User = entityManager.find(User::class.java, userId)

  private fun managedUserAccount(entityManager: EntityManager, username: String): UserAccount = entityManager.find(UserAccount::class.java, username)

  private fun managedCaseload(entityManager: EntityManager, caseloadId: String): Caseload = entityManager.find(Caseload::class.java, caseloadId)

  private fun <T : Any, ID : Any> saveUpdateDelete(
    entity: T,
    update: T.() -> T,
    save: (T) -> T,
    delete: (T) -> Unit,
    entityClass: Class<T>,
    auditId: ID,
  ): List<AuditRevision<T>> {
    val updatedEntity = save(entity.update())
    delete(updatedEntity)
    return auditRevisions(entityClass, auditId).also(::assertRevisionTypes)
  }

  private fun <T : Any, ID : Any> persistUpdateDelete(
    create: (EntityManager) -> ID,
    update: (EntityManager) -> T,
    delete: (EntityManager) -> Unit,
    entityClass: Class<T>,
  ): List<AuditRevision<T>> {
    val auditId = withTransaction(create)
    withTransaction(update)
    withTransaction(delete)
    return auditRevisions(entityClass, auditId).also(::assertRevisionTypes)
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
