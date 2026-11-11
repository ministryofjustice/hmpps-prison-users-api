package uk.gov.justice.digital.hmpps.prisonusersapi.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonusersapi.integration.IntegrationTestBase

class ReferenceDataResourceIntTest : IntegrationTestBase() {
  @DisplayName("GET /reference-data/caseloads")
  @Nested
  inner class GetCaseloads {

    @Test
    fun `access forbidden when no authority`() {
      webTestClient.get().uri("/reference-data/caseloads")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access allowed even when no roles specified`() {
      webTestClient.get().uri("/reference-data/caseloads")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isOk
    }

    @Test
    fun `access allowed for any role specified`() {
      webTestClient.get().uri("/reference-data/caseloads")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isOk
    }

    @Test
    fun `will return all active general caseloads ordered by name`() {
      webTestClient.get().uri("/reference-data/caseloads")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").value<Int> { assertThat(it > 158) }
        .jsonPath("$[?(@.id == 'AKI')]").exists()
        .jsonPath("$[0].name").isEqualTo("Acklington (HMP)")
        .jsonPath("$[0].id").isEqualTo("AKI")
        // central admin and DPS is filtered out
        .jsonPath("$[?(@.id == 'CADM_I')]").doesNotExist()
        .jsonPath("$[?(@.id == 'NWEB')]").doesNotExist()
    }
  }
}
