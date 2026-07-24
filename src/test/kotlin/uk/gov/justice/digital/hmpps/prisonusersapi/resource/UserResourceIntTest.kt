package uk.gov.justice.digital.hmpps.prisonusersapi.resource

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonusersapi.data.AccountStatus
import uk.gov.justice.digital.hmpps.prisonusersapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonusersapi.integration.helper.DataBuilder

class UserResourceIntTest : IntegrationTestBase() {
  @Autowired
  private lateinit var dataBuilder: DataBuilder

  @DisplayName("GET /users/basic/{username}")
  @Nested
  inner class GetUserBasicDetailsByUsername {

    @BeforeEach
    internal fun createUsers() {
      with(dataBuilder) {
        generalUser()
          .username("marco.rossi")
          .firstName("Marco")
          .lastName("Rossi")
          .status(AccountStatus.EXPIRED_GRACE)
          .buildAndSave()
      }
    }

    @AfterEach
    internal fun deleteUsers() {
      dataBuilder.deleteAll()
    }

    @Test
    fun `access unauthorized when no authority`() {
      webTestClient.get().uri("/users/basic/marco.rossi")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.get().uri("/users/basic/marco.rossi")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.get().uri("/users/basic/marco.rossi")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `get user not found`() {
      webTestClient.get().uri("/users/basic/dummy")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `get user with role ROLE_MAINTAIN_ACCESS_ROLES_ADMIN`() {
      webTestClient.get().uri("/users/basic/marco.rossi")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("username").isEqualTo("marco.rossi")
        .jsonPath("accountStatus").isEqualTo("EXPIRED_GRACE")
        .jsonPath("firstName").isEqualTo("Marco")
        .jsonPath("lastName").isEqualTo("Rossi")
        .jsonPath("enabled").isEqualTo("true")
        .jsonPath("activeCaseloadId").isEqualTo("WWI")
        .jsonPath("staffId").exists()
    }

    @Test
    fun `get user with role ROLE_MAINTAIN_ACCESS_ROLES`() {
      webTestClient.get().uri("/users/basic/marco.rossi")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("staffId").exists()
    }

    @Test
    fun `get user with role ROLE_MANAGE_NOMIS_USER_ACCOUNT`() {
      webTestClient.get().uri("/users/basic/marco.rossi")
        .headers(setAuthorisation(roles = listOf("ROLE_MANAGE_NOMIS_USER_ACCOUNT")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("staffId").exists()
    }

    @Test
    fun `get user with role ROLE_VIEW_NOMIS_STAFF_DETAILS`() {
      webTestClient.get().uri("/users/basic/marco.rossi")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_NOMIS_STAFF_DETAILS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("staffId").exists()
    }
  }

  @DisplayName("POST /users/basic/find-by-usernames")
  @Nested
  inner class FindUserBasicDetailsByUsernames {
    val uri = "/users/basic/find-by-usernames"

    @BeforeEach
    internal fun createUsers() {
      with(dataBuilder) {
        generalUser().username("marco.rossi").firstName("Marco").lastName("Rossi").status(AccountStatus.EXPIRED_GRACE)
          .buildAndSave()
      }
    }

    @AfterEach
    internal fun deleteUsers() = dataBuilder.deleteAll()

    @Test
    fun `access unauthorised when no authorisation`() {
      val usernames = listOf<String>()
      webTestClient.post().uri(uri)
        .bodyValue(usernames)
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      val usernames = listOf("marco.rossi")
      webTestClient.post().uri(uri)
        .bodyValue(usernames)
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `user forbidden with wrong role`() {
      val usernames = listOf("marco.rossi")
      webTestClient.post().uri(uri)
        .bodyValue(usernames)
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `user not found`() {
      val usernames = listOf("marco.rossi")
      webTestClient.post().uri(uri)
        .bodyValue(usernames)
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
        .exchange()
        .expectStatus().isOk
        .expectHeader().contentType(APPLICATION_JSON)
        .expectBody()
        .json("{}")
    }

    @ParameterizedTest(name = "access ok with authorised role {0}")
    @ValueSource(
      strings = [
        "ROLE_MAINTAIN_ACCESS_ROLES_ADMIN",
        "ROLE_MAINTAIN_ACCESS_ROLES",
        "ROLE_MANAGE_NOMIS_USER_ACCOUNT",
        "ROLE_VIEW_NOMIS_STAFF_DETAILS",
      ],
    )
    fun `access ok with authorised role {0}`(authorisedRole: String) {
      val username = "marco.rossi"
      val usernames = listOf(username)

      val spec = webTestClient.post().uri(uri)
        .bodyValue(usernames)
        .headers(setAuthorisation(roles = listOf(authorisedRole)))
        .exchange()
        .expectStatus().isOk
        .expectBody()

      assertUserJson(spec, username)
    }

    private fun assertUserJson(spec: WebTestClient.BodyContentSpec, username: String) {
      spec
        .jsonPath("['$username'].username").isEqualTo(username)
        .jsonPath("['$username'].accountStatus").isEqualTo("EXPIRED_GRACE")
        .jsonPath("['$username'].firstName").isEqualTo("Marco")
        .jsonPath("['$username'].lastName").isEqualTo("Rossi")
        .jsonPath("['$username'].enabled").isEqualTo("true")
        .jsonPath("['$username'].activeCaseloadId").isEqualTo("WWI")
        .jsonPath("['$username'].staffId").exists()
    }
  }
}
