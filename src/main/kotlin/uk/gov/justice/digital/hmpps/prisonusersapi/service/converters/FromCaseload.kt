package uk.gov.justice.digital.hmpps.prisonusersapi.service.converters

import uk.gov.justice.digital.hmpps.prisonusersapi.data.PrisonCaseload
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.Caseload

fun Caseload.toPrisonCaseload(): PrisonCaseload {
    return PrisonCaseload(
        id = this.id,
        name = this.name.capitalizeLeavingAbbreviations(),
        function = this.function
    )
}