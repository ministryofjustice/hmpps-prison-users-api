package uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccessibleCaseload
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.UserAccessibleCaseloadId

@Repository
interface UserAccessibleCaseloadRepository : JpaRepository<UserAccessibleCaseload, UserAccessibleCaseloadId>
