package uk.gov.justice.digital.hmpps.prisonusersapi.resource

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonusersapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonusersapi.integration.helper.DataBuilder

class UserCaseloadManagementResourceIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var dataBuilder: DataBuilder

  @DisplayName("GET /users/{username}/caseloads")
  @Nested
  inner class GetCaseloadsByUsername {
    @BeforeEach
    internal fun createUsers() {
      with(dataBuilder) {
        generalUser()
          .username("CASELOAD_USER1")
          .firstName("CASELOAD")
          .lastName("USER1")
          .atPrisons("BXI", "WWI")
          .buildAndSave()
      }
    }

    @AfterEach
    internal fun deleteAll() = dataBuilder.deleteAll()

    @Test
    fun `access forbidden when no authority`() {
      webTestClient.get().uri("/users/CASELOAD_USER1/caseloads")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.get().uri("/users/CASELOAD_USER1/caseloads")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `get user forbidden with wrong role`() {
      webTestClient.get().uri("/users/CASELOAD_USER1/caseloads")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `get user with caseloads not found`() {
      webTestClient.get().uri("/users/dummy/caseloads")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `get user with caseloads`() {
      webTestClient.get().uri("/users/CASELOAD_USER1/caseloads")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_ACCESS_ROLES_ADMIN")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("username").isEqualTo("CASELOAD_USER1")
        .jsonPath("activeCaseload.id").isEqualTo("BXI")
        .jsonPath("$.caseloads[?(@.id == 'NWEB')]").doesNotExist()
        .jsonPath("$.caseloads[?(@.id == 'BXI')]").exists()
        .jsonPath("$.caseloads[?(@.id == 'WWI')]").exists()

      webTestClient.get().uri("/users/CASELOAD_USER1/caseloads")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_NOMIS_STAFF_DETAILS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("username").isEqualTo("CASELOAD_USER1")
        .jsonPath("activeCaseload.id").isEqualTo("BXI")
        .jsonPath("$.caseloads[?(@.id == 'NWEB')]").doesNotExist()
        .jsonPath("$.caseloads[?(@.id == 'BXI')]").exists()
        .jsonPath("$.caseloads[?(@.id =='WWI')]").exists()
    }
  }
}
