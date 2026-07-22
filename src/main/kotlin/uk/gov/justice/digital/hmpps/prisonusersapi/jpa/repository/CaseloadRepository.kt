package uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.Caseload

@Repository
interface CaseloadRepository : JpaRepository<Caseload, String> {
    fun findByActiveAndFunctionAndAdministrationCaseloadTrueOrderByNameAsc(active: Boolean, function: String): List<Caseload>
}
