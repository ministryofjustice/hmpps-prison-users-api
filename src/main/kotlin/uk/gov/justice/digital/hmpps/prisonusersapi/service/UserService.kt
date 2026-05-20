package uk.gov.justice.digital.hmpps.prisonusersapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserCaseloadDetail
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserAccountRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.service.converters.toUserCaseloadDetail
import java.util.function.Supplier

@Service
@Transactional
class UserService(
  private val userAccountRepository: UserAccountRepository,
) {

  @Transactional(readOnly = true)
  fun getCaseloads(username: String): UserCaseloadDetail =
    userAccountRepository.findById(username).orElseThrow(UserNotFoundException("User $username not found"))!!
      .toUserCaseloadDetail(removeDpsCaseload = true)
}

class UserNotFoundException(message: String?) :
  RuntimeException(message),
  Supplier<UserNotFoundException> {
  override fun get(): UserNotFoundException = UserNotFoundException(message)
}
