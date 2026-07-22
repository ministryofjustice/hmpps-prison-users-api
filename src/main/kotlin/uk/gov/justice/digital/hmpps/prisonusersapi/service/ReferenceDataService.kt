package uk.gov.justice.digital.hmpps.prisonusersapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonusersapi.data.PrisonCaseload
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.Caseload.Companion.GENERAL_CASELOAD
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.CaseloadRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.service.converters.toPrisonCaseload

@Service
class ReferenceDataService(private val caseloadRepository: CaseloadRepository) {

    fun getActiveGeneralCaseloads(): List<PrisonCaseload> = caseloadRepository.findByActiveAndFunctionAndAdministrationCaseloadTrueOrderByNameAsc(true, GENERAL_CASELOAD)
        .map { it.toPrisonCaseload() }
}