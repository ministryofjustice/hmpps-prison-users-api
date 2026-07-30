package uk.gov.justice.digital.hmpps.prisonusersapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserBasicDetails
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserCaseloadDetail
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserDetail
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserAccountRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.service.converters.toUserBasicDetails
import uk.gov.justice.digital.hmpps.prisonusersapi.service.converters.toUserCaseloadDetail
import uk.gov.justice.digital.hmpps.prisonusersapi.service.converters.toUserDetail
import java.util.function.Supplier

@Service
@Transactional
class UserService(
  private val userAccountRepository: UserAccountRepository,
) {

  @Transactional(readOnly = true)
  fun getCaseloads(username: String): UserCaseloadDetail = userAccountRepository.findById(username).orElseThrow(UserNotFoundException("User $username not found"))
    .toUserCaseloadDetail(removeDpsCaseload = true)

  @Transactional(readOnly = true)
  fun findUserBasicDetails(username: String) = userAccountRepository.findWithUserAndActiveCaseloadByUsername(username).orElseThrow(UserNotFoundException("User not found: $username not found")).toUserBasicDetails()

  @Transactional(readOnly = true)
  fun findUserBasicDetails(usernames: List<String>): Map<String, UserBasicDetails> {
    if (usernames.isEmpty()) return emptyMap()
    log.info("Fetching user basic details for {} usernames", usernames.size)
    val userDetails = userAccountRepository.findWithUserAndActiveCaseloadByUsernameIn(usernames)
      .map { it.toUserBasicDetails() }
      .associateBy { it.username }
    log.info("Returning {} user basic details for {} usernames", userDetails.size, usernames.size)
    return userDetails
  }

  @Transactional(readOnly = true)
  fun findByUsername(username: String): UserDetail = userAccountRepository.findWithUserAndActiveCaseloadAndUserRoleCodesByUsername(username)
    .map { it.toUserDetail() }
    .orElseThrow(UserNotFoundException("User $username not found"))

  companion object {
    private val log = LoggerFactory.getLogger(UserService::class.java)
  }
}

class UserNotFoundException(message: String?) :
  RuntimeException(message),
  Supplier<UserNotFoundException> {
  override fun get(): UserNotFoundException = UserNotFoundException(message)
}
