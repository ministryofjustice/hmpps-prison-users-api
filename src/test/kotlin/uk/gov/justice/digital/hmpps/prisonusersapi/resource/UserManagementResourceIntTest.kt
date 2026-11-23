package uk.gov.justice.digital.hmpps.prisonusersapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonusersapi.data.AccountStatus
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserAccountDetails
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserDetails
import uk.gov.justice.digital.hmpps.prisonusersapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonusersapi.integration.helper.DataBuilder
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.SyncLock
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.SyncLockRepository
import java.time.LocalDateTime

@TestPropertySource(properties = ["sync.lock.max-wait-ms=100"])
class UserManagementResourceIntTest(
  @Autowired private val dataBuilder: DataBuilder,
  @Autowired private val syncLockRepository: SyncLockRepository,
) : SqsIntegrationTestBase() {

  @DisplayName("PUT /{username}/unlock-user")
  @Nested
  inner class UnlockUser {
    private var legacyStaffId: Long = 0

    @BeforeEach
    internal fun createUsers() {
      with(dataBuilder) {
        legacyStaffId = generalUser()
          .username("marco.rossi")
          .firstName("Marco")
          .lastName("Rossi")
          .status(AccountStatus.EXPIRED_LOCKED)
          .buildAndSave()
          .user.legacyStaffId
      }
    }

    @AfterEach
    internal fun deleteUsers() {
      dataBuilder.deleteAll()
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/users/marco.rossi/unlock-user")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/users/marco.rossi/unlock-user")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `get user forbidden with wrong role`() {
        webTestClient.put().uri("/users/marco.rossi/unlock-user")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `user name too long`() {
        webTestClient.put().uri("/users/thisusernameiswaytoolongtobevalid/unlock-user")
          .headers(setAuthorisation(roles = listOf("ROLE_MANAGE_NOMIS_USER_ACCOUNT")))
          .exchange()
          .expectStatus().isBadRequest
      }
    }

    @Nested
    inner class Failure {
      @Test
      fun `user not found`() {
        webTestClient.put().uri("/users/not-found/unlock-user")
          .headers(setAuthorisation(roles = listOf("ROLE_MANAGE_NOMIS_USER_ACCOUNT")))
          .exchange()
          .expectStatus().isNotFound
      }

      @Test
      fun `should return conflict when sync lock is not available`() {
        syncLockRepository.saveAndFlush(
          SyncLock(
            legacyStaffId = legacyStaffId,
            lockedAt = LocalDateTime.now(),
            lockedBy = "another-sync",
          ),
        )

        try {
          webTestClient.put().uri("/users/marco.rossi/unlock-user")
            .headers(setAuthorisation(roles = listOf("ROLE_MANAGE_NOMIS_USER_ACCOUNT")))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.status").isEqualTo(409)
            .jsonPath("$.userMessage").isEqualTo("Conflict: Unable to acquire sync lock")
            .jsonPath("$.developerMessage").isEqualTo("Timeout waiting for sync lock for legacy staff id $legacyStaffId")

          assertThat(syncLockRepository.findById(legacyStaffId).orElseThrow().lockedBy).isEqualTo("another-sync")

          webTestClient.get().uri("/users/marco.rossi")
            .headers(setAuthorisation(roles = listOf("ROLE_MANAGE_NOMIS_USER_ACCOUNT")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.accountStatus").isEqualTo(AccountStatus.EXPIRED_LOCKED.name)
            .jsonPath("$.accountNonLocked").isEqualTo(false)
        } finally {
          syncLockRepository.deleteById(legacyStaffId)
        }
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `should unlock user and post correct domain event`() {
        val userAccountDetails = webTestClient.get().uri("/users/marco.rossi")
          .headers(setAuthorisation(roles = listOf("ROLE_MANAGE_NOMIS_USER_ACCOUNT")))
          .exchange()
          .expectStatus().isOk
          .expectBody<UserAccountDetails>()
          .returnResult()
          .responseBody!!

        assertThat(userAccountDetails.accountStatus).isEqualTo(AccountStatus.EXPIRED_LOCKED)
        assertThat(userAccountDetails.accountNonLocked).isEqualTo(false)

        webTestClient.put().uri("/users/marco.rossi/unlock-user")
          .headers(setAuthorisation(roles = listOf("ROLE_MANAGE_NOMIS_USER_ACCOUNT")))
          .exchange()
          .expectStatus().isOk

        webTestClient.get().uri("/users/marco.rossi")
          .headers(setAuthorisation(roles = listOf("ROLE_MANAGE_NOMIS_USER_ACCOUNT")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.accountStatus").isEqualTo(AccountStatus.OPEN.name)
          .jsonPath("$.accountNonLocked").isEqualTo(true)

        assertThatDomainEventWasSent(
          eventType = "prison-users.user.account.unlocked",
          legacyStaffId = userAccountDetails.staffId,
          username = userAccountDetails.username,
        )
      }
    }
  }

  @DisplayName("PUT /{username}/change-name")
  @Nested
  inner class ChangeName {
    private var legacyStaffId: Long = 0

    @BeforeEach
    internal fun createUser() {
      legacyStaffId = with(dataBuilder) {
        generalUser()
          .username("marco.rossi")
          .firstName("Marco")
          .lastName("Rossi")
          .buildAndSave()
          .user.legacyStaffId
      }
    }

    @AfterEach
    internal fun deleteUsers() {
      dataBuilder.deleteAll()
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/users/marco.rossi/change-name")
          .bodyValue(mapOf("firstName" to "Jane", "lastName" to "Smith"))
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/users/marco.rossi/change-name")
          .headers(setAuthorisation(roles = listOf()))
          .bodyValue(mapOf("firstName" to "Jane", "lastName" to "Smith"))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/users/marco.rossi/change-name")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .bodyValue(mapOf("firstName" to "Jane", "lastName" to "Smith"))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `first name must contain only permitted characters`() {
        assertInvalidName(firstName = "Marco Rossi", lastName = "Rossi")
      }

      @Test
      fun `last name must contain only permitted characters`() {
        assertInvalidName(firstName = "Marco", lastName = "Rossi!")
      }

      @Test
      fun `first name must not be empty`() {
        assertInvalidName(firstName = "", lastName = "Rossi")
      }

      @Test
      fun `last name must not be empty`() {
        assertInvalidName(firstName = "Marco", lastName = "")
      }

      @Test
      fun `first name must not exceed 35 characters`() {
        assertInvalidName(firstName = "A".repeat(36), lastName = "Rossi")
      }

      @Test
      fun `last name must not exceed 35 characters`() {
        assertInvalidName(firstName = "Marco", lastName = "A".repeat(36))
      }

      @Test
      fun `username must not exceed 30 characters`() {
        webTestClient.put().uri("/users/thisusernameiswaytoolongtobevalid/change-name")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
          .bodyValue(mapOf("firstName" to "Marco", "lastName" to "Rossi"))
          .exchange()
          .expectStatus().isBadRequest
      }

      private fun assertInvalidName(firstName: String, lastName: String) {
        webTestClient.put().uri("/users/marco.rossi/change-name")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
          .bodyValue(mapOf("firstName" to firstName, "lastName" to lastName))
          .exchange()
          .expectStatus().isBadRequest
      }
    }

    @Nested
    inner class Failure {
      @Test
      fun `user not found`() {
        webTestClient.put().uri("/users/not-found/change-name")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
          .bodyValue(mapOf("firstName" to "Marco", "lastName" to "Rossi"))
          .exchange()
          .expectStatus().isNotFound
      }

      @Test
      fun `should return conflict when sync lock is not available`() {
        syncLockRepository.saveAndFlush(
          SyncLock(
            legacyStaffId = legacyStaffId,
            lockedAt = LocalDateTime.now(),
            lockedBy = "another-sync",
          ),
        )

        try {
          webTestClient.put().uri("/users/marco.rossi/change-name")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
            .bodyValue(mapOf("firstName" to "Jane", "lastName" to "Smith"))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.status").isEqualTo(409)
            .jsonPath("$.userMessage").isEqualTo("Conflict: Unable to acquire sync lock")
            .jsonPath("$.developerMessage").isEqualTo("Timeout waiting for sync lock for legacy staff id $legacyStaffId")

          assertThat(syncLockRepository.findById(legacyStaffId).orElseThrow().lockedBy).isEqualTo("another-sync")

          webTestClient.get().uri("/users/marco.rossi")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.firstName").isEqualTo("Marco")
            .jsonPath("$.lastName").isEqualTo("Rossi")
        } finally {
          syncLockRepository.deleteById(legacyStaffId)
        }
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `should update user name and publish correct domain event`() {
        webTestClient.put().uri("/users/marco.rossi/change-name")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
          .bodyValue(mapOf("firstName" to "Jane", "lastName" to "O'Neil"))
          .exchange()
          .expectStatus().isOk
          .expectBody<UserDetails>()
          .returnResult()
          .responseBody!!
          .also {
            assertThat(it.firstName).isEqualTo("Jane")
            assertThat(it.lastName).isEqualTo("O'Neil")
            assertThat(it.staffId).isEqualTo(legacyStaffId)
          }

        webTestClient.get().uri("/users/marco.rossi")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.firstName").isEqualTo("Jane")
          .jsonPath("$.lastName").isEqualTo("O'neil")

        assertThatDomainEventWasSent(
          eventType = "prison-users.user.name.updated",
          legacyStaffId = legacyStaffId,
          username = "marco.rossi",
        )
      }
    }
  }

  @DisplayName("PUT /{username}/change-email")
  @Nested
  inner class ChangeEmail {
    private var legacyStaffId: Long = 0

    @BeforeEach
    internal fun createUser() {
      legacyStaffId = with(dataBuilder) {
        generalUser()
          .username("marco.rossi")
          .firstName("Marco")
          .lastName("Rossi")
          .withPrimaryEmail("marco.rossi@example.com")
          .buildAndSave()
          .user.legacyStaffId
      }
    }

    @AfterEach
    internal fun deleteUsers() {
      dataBuilder.deleteAll()
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/users/marco.rossi/change-email")
          .bodyValue("marco.new@example.com")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/users/marco.rossi/change-email")
          .headers(setAuthorisation(roles = listOf()))
          .bodyValue("marco.new@example.com")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/users/marco.rossi/change-email")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .bodyValue("marco.new@example.com")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `email must be valid`() {
        webTestClient.put().uri("/users/marco.rossi/change-email")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
          .bodyValue("not-an-email")
          .exchange()
          .expectStatus().isBadRequest
      }

      @Test
      fun `username must not exceed 30 characters`() {
        webTestClient.put().uri("/users/thisusernameiswaytoolongtobevalid/change-email")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
          .bodyValue("marco.new@example.com")
          .exchange()
          .expectStatus().isBadRequest
      }
    }

    @Nested
    inner class Failure {
      @Test
      fun `user not found`() {
        webTestClient.put().uri("/users/not-found/change-email")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
          .bodyValue("marco.new@example.com")
          .exchange()
          .expectStatus().isNotFound
      }

      @Test
      fun `should return conflict when sync lock is not available`() {
        syncLockRepository.saveAndFlush(
          SyncLock(
            legacyStaffId = legacyStaffId,
            lockedAt = LocalDateTime.now(),
            lockedBy = "another-sync",
          ),
        )

        try {
          webTestClient.put().uri("/users/marco.rossi/change-email")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
            .bodyValue("marco.new@example.com")
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.status").isEqualTo(409)
            .jsonPath("$.userMessage").isEqualTo("Conflict: Unable to acquire sync lock")
            .jsonPath("$.developerMessage").isEqualTo("Timeout waiting for sync lock for legacy staff id $legacyStaffId")

          assertThat(syncLockRepository.findById(legacyStaffId).orElseThrow().lockedBy).isEqualTo("another-sync")

          webTestClient.get().uri("/users/marco.rossi")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.primaryEmail").isEqualTo("marco.rossi@example.com")
        } finally {
          syncLockRepository.deleteById(legacyStaffId)
        }
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `should handle update using the existing email address`() {
        webTestClient.put().uri("/users/marco.rossi/change-email")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
          .bodyValue("marco.rossi@example.com")
          .exchange()
          .expectStatus().isOk
          .expectBody<UserDetails>()
          .returnResult()
          .responseBody!!
          .also {
            assertThat(it.primaryEmail).isEqualTo("marco.rossi@example.com")
            assertThat(it.staffId).isEqualTo(legacyStaffId)
          }

        webTestClient.get().uri("/users/marco.rossi")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.primaryEmail").isEqualTo("marco.rossi@example.com")
      }

      @Test
      fun `should update user email and publish correct domain event`() {
        webTestClient.put().uri("/users/marco.rossi/change-email")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
          .bodyValue("marco.new@example.com")
          .exchange()
          .expectStatus().isOk
          .expectBody<UserDetails>()
          .returnResult()
          .responseBody!!
          .also {
            assertThat(it.firstName).isEqualTo("Marco")
            assertThat(it.lastName).isEqualTo("Rossi")
            assertThat(it.primaryEmail).isEqualTo("marco.new@example.com")
            assertThat(it.staffId).isEqualTo(legacyStaffId)
          }

        webTestClient.get().uri("/users/marco.rossi")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.primaryEmail").isEqualTo("marco.new@example.com")

        assertThatDomainEventWasSent(
          eventType = "prison-users.user.email.updated",
          legacyStaffId = legacyStaffId,
          username = "marco.rossi",
        )
      }
    }
  }
}
