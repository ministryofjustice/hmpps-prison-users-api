package uk.gov.justice.digital.hmpps.prisonusersapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonusersapi.data.AccountStatus
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserDetail
import uk.gov.justice.digital.hmpps.prisonusersapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonusersapi.integration.helper.DataBuilder

class UserManagementResourceIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var dataBuilder: DataBuilder

  @DisplayName("PUT /{username}/unlock-user")
  @Nested
  inner class UnlockUser {
    @BeforeEach
    internal fun createUsers() {
      with(dataBuilder) {
        generalUser()
          .username("marco.rossi")
          .firstName("Marco")
          .lastName("Rossi")
          .status(AccountStatus.EXPIRED_LOCKED)
          .buildAndSave()
      }
    }

    @AfterEach
    internal fun deleteUsers() {
      dataBuilder.deleteAll()
    }

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

    @Test
    fun `user not found`() {
      webTestClient.put().uri("/users/not-found/unlock-user")
        .headers(setAuthorisation(roles = listOf("ROLE_MANAGE_NOMIS_USER_ACCOUNT")))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `should unlock user and post correct domain event`() {
      val userDetail = webTestClient.get().uri("/users/marco.rossi")
        .headers(setAuthorisation(roles = listOf("ROLE_MANAGE_NOMIS_USER_ACCOUNT")))
        .exchange()
        .expectStatus().isOk
        .expectBody<UserDetail>()
        .returnResult()
        .responseBody!!

      assertThat(userDetail.accountStatus).isEqualTo(AccountStatus.EXPIRED_LOCKED)
      assertThat(userDetail.accountNonLocked).isEqualTo(false)

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
        eventType = "user.account.unlocked",
        legacyStaffId = userDetail.staffId,
        username = userDetail.username,
      )
    }
  }
}
