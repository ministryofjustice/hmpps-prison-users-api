package uk.gov.justice.digital.hmpps.prisonusersapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonusersapi.data.reconciliation.PrisonUserReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserAccountRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserRoleRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UsersRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.service.converters.toPrisonUserReconciliationResponse

@Service
@Transactional(readOnly = true)
class ReconciliationService(
  private val usersRepository: UsersRepository,
  private val userAccountRepository: UserAccountRepository,
  private val userRoleRepository: UserRoleRepository,
) {
  fun getPrisonUserForReconciliation(legacyStaffId: Long): PrisonUserReconciliationResponse {
    val user = usersRepository.findByLegacyStaffId(legacyStaffId)
      .orElseThrow(UserNotFoundException("User with legacy staff id $legacyStaffId not found"))

    val userAccounts = userAccountRepository.findAllByUserUserId(requireNotNull(user.userId))
    val usernames = userAccounts.map { it.username }
    val userRolesByUsername = if (usernames.isEmpty()) emptyMap() else userRoleRepository.findAllByIdUsernameIn(usernames).groupBy { it.id.username }

    return user.toPrisonUserReconciliationResponse(userAccounts, userRolesByUsername)
  }
}

