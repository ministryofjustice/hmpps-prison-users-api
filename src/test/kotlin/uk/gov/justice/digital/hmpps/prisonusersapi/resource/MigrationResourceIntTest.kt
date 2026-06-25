package uk.gov.justice.digital.hmpps.prisonusersapi.resource

import org.apache.http.HttpStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonusersapi.data.AccountStatus
import uk.gov.justice.digital.hmpps.prisonusersapi.data.MigratedUser
import uk.gov.justice.digital.hmpps.prisonusersapi.data.MigratedUserAccessibleCaseload
import uk.gov.justice.digital.hmpps.prisonusersapi.data.MigratedUserAccount
import uk.gov.justice.digital.hmpps.prisonusersapi.data.MigratedUserEmail
import uk.gov.justice.digital.hmpps.prisonusersapi.data.MigratedUserRole
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UsageType
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserMigrationRequest
import uk.gov.justice.digital.hmpps.prisonusersapi.data.UserStatus
import uk.gov.justice.digital.hmpps.prisonusersapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonusersapi.integration.helper.DataBuilder
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserAccessibleCaseloadRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserAccountRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserEmailsRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UserRoleRepository
import uk.gov.justice.digital.hmpps.prisonusersapi.jpa.repository.UsersRepository
import java.time.LocalDateTime

class MigrationResourceIntTest : IntegrationTestBase() {

    @Autowired
    private lateinit var userAccountRepository: UserAccountRepository

    @Autowired
    private lateinit var userAccessibleCaseloadRepository: UserAccessibleCaseloadRepository

    @Autowired
    private lateinit var userRoleRepository: UserRoleRepository

    @Autowired
    private lateinit var userRepository: UsersRepository

    @Autowired
    private lateinit var userEmailsRepository: UserEmailsRepository

    @Autowired
    private lateinit var dataBuilder: DataBuilder

    @DisplayName("POST /migrate/user")
    @Nested
    inner class MigrateUser {

        @AfterEach
        internal fun deleteUsers(): Unit = dataBuilder.deleteAll()

        @Test
        fun `access forbidden when no authority`() {
            webTestClient.post().uri("/migrate/user")
                .body(
                    BodyInserters.fromValue(
                        UserMigrationRequest(
                            user = migratedUser(),
                            accounts = listOf(userAccount()),
                            roles = null,
                            accessibleCaseloads = null
                        )
                    )
                )
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        fun accessForbiddenWhenNoRole() {
            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf()))
                .body(
                    BodyInserters.fromValue(
                        UserMigrationRequest(
                            user = migratedUser(),
                            accounts = listOf(userAccount()),
                            roles = null,
                            accessibleCaseloads = null
                        )
                    )
                )
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun accessForbiddenWithWrongRole() {
            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
                .body(
                    BodyInserters.fromValue(
                        UserMigrationRequest(
                            user = migratedUser(),
                            accounts = listOf(userAccount()),
                            roles = null,
                            accessibleCaseloads = null
                        )
                    )
                )
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun accessibleCaseloadNotFound() {
            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
                .body(
                    BodyInserters.fromValue(
                        UserMigrationRequest(
                            user = migratedUser(),
                            accounts = listOf(userAccount()),
                            roles = null,
                            accessibleCaseloads = listOf(accessibleCaseload("test_one", "NOT_A_CASELOAD"))
                        )
                    )
                )
                .exchange()
                .expectStatus().isNotFound
        }

        @Test
        fun userAlreadyExists() {
            val userMigrationRequest = UserMigrationRequest(
                user = migratedUser(),
                accounts = listOf(userAccount()),
                roles = null,
                accessibleCaseloads = null
            )

            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
                .body(
                    BodyInserters.fromValue(
                        userMigrationRequest
                    )
                )
                .exchange()
                .expectStatus().isOk

            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
                .body(
                    BodyInserters.fromValue(
                        userMigrationRequest
                    )
                )
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SC_CONFLICT)
                .expectBody()
                .jsonPath("userMessage")
                .isEqualTo("User already exists: User with legacy staff id ${userMigrationRequest.user!!.id} already exists")
        }

        @Test
        fun activeCaseloadNotFoundAndNoUserAccessibleCaseloads() {
            assertTrue(userAccountRepository.findByUsername("testy").isEmpty)

            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
                .body(
                    BodyInserters.fromValue(
                        UserMigrationRequest(
                            user = migratedUser(),
                            accounts = listOf(userAccount(username = "testy", activeCaseloadId = "NOT_A_CASELOAD")),
                            roles = null,
                            accessibleCaseloads = null
                        )
                    )
                )
                .exchange()
                .expectStatus().isOk

            val optionalUserAccount = userAccountRepository.findByUsername("testy")
            assertTrue(optionalUserAccount.isPresent)
            assertTrue { optionalUserAccount.get().activeCaseload == null }
        }

        @Test
        fun activeCaseloadNotFoundAndUserAccessibleCaseloadsPresent() {
            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
                .body(
                    BodyInserters.fromValue(
                        UserMigrationRequest(
                            user = migratedUser(),
                            accounts = listOf(userAccount(username = "testy", activeCaseloadId = "NOT_A_CASELOAD")),
                            roles = null,
                            accessibleCaseloads = listOf(accessibleCaseload(username = "testy", caseloadId = "MDI"), accessibleCaseload(username = "testy", caseloadId = "LEI"))
                        )
                    )
                )
                .exchange()
                .expectStatus().isOk

            val optionalUserAccount = userAccountRepository.findByUsername("testy")
            assertTrue(optionalUserAccount.isPresent)
            assertNotNull(optionalUserAccount.get().activeCaseload)
            assertTrue { optionalUserAccount.get().activeCaseload?.id == "MDI" }
        }

        @Test
        fun activeCaseloadIdSetAndFound() {
            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
                .body(
                    BodyInserters.fromValue(
                        UserMigrationRequest(
                            user = migratedUser(),
                            accounts = listOf(userAccount(username = "testy", activeCaseloadId = "MDI")),
                            roles = null,
                            accessibleCaseloads = listOf(accessibleCaseload(username = "testy", caseloadId = "LEI"), accessibleCaseload(username = "testy", caseloadId = "MDI"))
                        )
                    )
                )
                .exchange()
                .expectStatus().isOk

            val optionalUserAccount = userAccountRepository.findByUsername("testy")
            assertTrue(optionalUserAccount.isPresent)
            assertNotNull(optionalUserAccount.get().activeCaseload)
            assertTrue { optionalUserAccount.get().activeCaseload?.id == "MDI" }
        }

        @Test
        fun activeCaseloadIdPresentSetAndFound() {
            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
                .body(
                    BodyInserters.fromValue(
                        UserMigrationRequest(
                            user = migratedUser(),
                            accounts = listOf(userAccount(username = "testy", activeCaseloadId = "MDI")),
                            roles = null,
                            accessibleCaseloads = listOf(accessibleCaseload(username = "testy", caseloadId = "LEI"), accessibleCaseload(username = "testy", caseloadId = "MDI"))
                        )
                    )
                )
                .exchange()
                .expectStatus().isOk

            val optionalUserAccount = userAccountRepository.findByUsername("testy")
            assertTrue(optionalUserAccount.isPresent)
            assertNotNull(optionalUserAccount.get().activeCaseload)
            assertTrue { optionalUserAccount.get().activeCaseload?.id == "MDI" }
        }

        @Test
        fun activeCaseloadIdOnlyPresentInMigratedUserAccessibleCaseloadRelatingToDifferentUserAccount() {
            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
                .body(
                    BodyInserters.fromValue(
                        UserMigrationRequest(
                            user = migratedUser(),
                            accounts = listOf(userAccount(username = "testy-1", activeCaseloadId = "MDI"), userAccount(username = "testy-2", activeCaseloadId = "LEI")),
                            roles = null,
                            accessibleCaseloads = listOf(accessibleCaseload(username = "testy-2", caseloadId = "MDI"), accessibleCaseload(username = "testy-1", caseloadId = "LEI"))
                        )
                    )
                )
                .exchange()
                .expectStatus().isOk

            val optionalUserAccount = userAccountRepository.findByUsername("testy-1")
            assertTrue(optionalUserAccount.isPresent)
            assertNotNull(optionalUserAccount.get().activeCaseload)
            assertTrue { optionalUserAccount.get().activeCaseload?.id == "LEI" }
        }

        @Test
        fun activeCaseloadIdSetToNullWhenNoActiveCaseloadIdSetAndNoUserAccessibleCaseloads() {
            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
                .body(
                    BodyInserters.fromValue(
                        UserMigrationRequest(
                            user = migratedUser(),
                            accounts = listOf(userAccount(username = "testy-1"), userAccount(username = "testy-2")),
                            roles = null,
                            accessibleCaseloads = null
                        )
                    )
                )
                .exchange()
                .expectStatus().isOk

            val optionalUserAccount = userAccountRepository.findByUsername("testy-1")
            assertTrue(optionalUserAccount.isPresent)
            assertNull(optionalUserAccount.get().activeCaseload)
        }

        @Test
        fun userRoleWithoutUserAccount() {
            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
                .body(
                    BodyInserters.fromValue(
                        UserMigrationRequest(
                            user = migratedUser(),
                            accounts = listOf(userAccount(username = "testy")),
                            roles = listOf(userRole(username = "testy-xxx", roleCode = "ROLE_BANANAS")),
                            accessibleCaseloads = null
                        )
                    )
                )
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("userMessage")
                .isEqualTo("Validation failure: User account for username testy-xxx not found during user role migration")

        }

        @Test
        fun caseloadWithoutUserAccount() {
            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
                .body(
                    BodyInserters.fromValue(
                        UserMigrationRequest(
                            user = migratedUser(),
                            accounts = listOf(userAccount(username = "testy", activeCaseloadId = "MDI")),
                            roles = null,
                            accessibleCaseloads = listOf(accessibleCaseload(username = "testy-1", caseloadId = "MDI"))
                        )
                    )
                )
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("userMessage")
                .isEqualTo("Validation failure: User account for username testy-1 not found during user accessible caseload migration")
        }

        @Test
        fun completeRequestAndResponse() {
            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
                .body(
                    BodyInserters.fromValue(
                        UserMigrationRequest(
                            user = migratedUser(),
                            accounts = listOf(userAccount(username = "testy", activeCaseloadId = "MDI"), userAccount(username = "testy-1", activeCaseloadId = "LEI")),
                            roles = listOf(userRole(username = "testy", roleCode = "ROLE_BANANAS"), userRole(username = "testy-1", roleCode = "ROLE_STRAWBERRIES")),
                            accessibleCaseloads = listOf(
                                accessibleCaseload(username = "testy", caseloadId = "MDI"),
                                accessibleCaseload(username = "testy", caseloadId = "LEI"),
                                accessibleCaseload(username = "testy-1", caseloadId = "MDI"),
                                accessibleCaseload(username = "testy-1", caseloadId = "LEI"),
                                )
                        )
                    )
                )
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.userId").isNotEmpty
                .jsonPath("$.staffId").isEqualTo(migratedUser().id)

            val testy11UserAccount = userAccountRepository.findByUsername("testy")
            val testy12UserAccount = userAccountRepository.findByUsername("testy-1")

            assertTrue(testy11UserAccount.isPresent)
            assertTrue(testy12UserAccount.isPresent)

            assertNotNull(testy11UserAccount.get().activeCaseload)
            assertNotNull(testy12UserAccount.get().activeCaseload)

            assertTrue { testy11UserAccount.get().activeCaseload?.id == "MDI" }
            assertTrue { testy12UserAccount.get().activeCaseload?.id == "LEI" }

            val userAccessibleCaseloadsByUsername = userAccessibleCaseloadRepository.findAll().groupBy { it.userAccount.username }

            assertTrue { userAccessibleCaseloadsByUsername["testy"]?.map { it.caseload.id }?.containsAll(listOf("MDI", "LEI"))!! }
            assertTrue { userAccessibleCaseloadsByUsername["testy-1"]?.map { it.caseload.id }?.containsAll(listOf("MDI", "LEI"))!! }

            val userRolesByUsername = userRoleRepository.findAll().groupBy { it.id.username }

            assertTrue { userRolesByUsername["testy"]?.size == 1 }
            assertTrue { userRolesByUsername["testy-1"]?.size == 1 }

            assertTrue { userRolesByUsername["testy"]?.get(0)?.id?.roleCode == "ROLE_BANANAS" }
            assertTrue { userRolesByUsername["testy-1"]?.get(0)?.id?.roleCode == "ROLE_STRAWBERRIES" }

            val allUsers = userRepository.findAll()
            assertTrue { allUsers.size == 1 }

            val emailsByUserId = userEmailsRepository.findAll().groupBy { it.id.userId }
            assertTrue { emailsByUserId.size == 1 }
            assertTrue { emailsByUserId.entries.flatMap { it.value }.map { it.id.email }.containsAll<Any>(listOf("test@email.com", "test-2@email.com")) }
        }

        @Test
        fun requestWithNoUserInvalid() {
            val request: Map<String, Any?> = mapOf(
                "user" to null,
                "accounts" to listOf(userAccount(username = "testy", activeCaseloadId = "MDI"), userAccount(username = "testy-1", activeCaseloadId = "LEI")),
                "roles" to listOf(userRole(username = "testy", roleCode = "ROLE_BANANAS"), userRole(username = "testy-1", roleCode = "ROLE_STRAWBERRIES")),
                "accessibleCaseloads" to listOf(
                    accessibleCaseload(username = "testy", caseloadId = "MDI"),
                    accessibleCaseload(username = "testy", caseloadId = "LEI"),
                    accessibleCaseload(username = "testy-1", caseloadId = "MDI"),
                    accessibleCaseload(username = "testy-1", caseloadId = "LEI"),
                )
            )

            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("userMessage")
                .isEqualTo("Validation failure: user Expected one 'user'")
        }

        @Test
        fun requestWithNoUserAccountsInvalid() {
            val request: Map<String, Any?> = mapOf(
                "user" to migratedUser(),
                "accounts" to emptyList<MigratedUserAccount>(),
                "roles" to listOf(userRole(username = "testy", roleCode = "ROLE_BANANAS"), userRole(username = "testy-1", roleCode = "ROLE_STRAWBERRIES")),
                "accessibleCaseloads" to listOf(
                    accessibleCaseload(username = "testy", caseloadId = "MDI"),
                    accessibleCaseload(username = "testy", caseloadId = "LEI"),
                    accessibleCaseload(username = "testy-1", caseloadId = "MDI"),
                    accessibleCaseload(username = "testy-1", caseloadId = "LEI"),
                )
            )

            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("userMessage")
                .isEqualTo("Validation failure: accounts Expected at least one 'user account'")
        }

        @Test
        fun requestWithIncompleteUserInvalid() {
            val request: Map<String, Any?> = mapOf(
                "user" to mapOf<String, Any?>(
                    "modifiedBy" to "Test",
                ),
                "accounts" to listOf(userAccount(username = "testy", activeCaseloadId = "MDI"), userAccount(username = "testy-1", activeCaseloadId = "LEI")),
                "roles" to listOf(userRole(username = "testy", roleCode = "ROLE_BANANAS"), userRole(username = "testy-1", roleCode = "ROLE_STRAWBERRIES")),
                "accessibleCaseloads" to listOf(
                    accessibleCaseload(username = "testy", caseloadId = "MDI"),
                    accessibleCaseload(username = "testy", caseloadId = "LEI"),
                    accessibleCaseload(username = "testy-1", caseloadId = "MDI"),
                    accessibleCaseload(username = "testy-1", caseloadId = "LEI"),
                )
            )

            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("userMessage")
                .isEqualTo("Validation failure: user.createdBy Expected created by, user.createdTimestamp Expected created timestamp, user.firstName Expected first name, user.id Expected user id, user.lastName Expected last name, user.status Expected user status")

        }

        @Test
        fun requestWithIncompleteUserAccountInvalid() {
            val incompleteUserAccount: Map<String, Any?> = mapOf(
                "activeCaseloadId" to "LEI"
            )

            val request: Map<String, Any?> = mapOf(
                "user" to migratedUser(),
                "accounts" to listOf(incompleteUserAccount)
            )

            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("userMessage")
                .isEqualTo("Validation failure: accounts[0].accountStatus Expected account status, accounts[0].accountType Expected account type, accounts[0].createdBy Expected created by, accounts[0].createdTimestamp Expected created timestamp, accounts[0].username Expected username")
        }

        @Test
        fun requestWithIncompleteUserEmailInvalid() {
            val request: Map<String, Any?> = mapOf(
                "user" to mapOf<String, Any?>(
                    "id" to Long.MAX_VALUE,
                    "emails" to listOf(
                        mapOf<String, Any?>(
                        "modifiedBy" to "Test"
                    )),
                    "firstName" to "Test",
                    "lastName" to "User",
                    "status" to UserStatus.ACTIVE,
                    "createdTimestamp" to LocalDateTime.now(),
                    "createdBy" to "TEST_USER"
                ),
                "accounts" to listOf(userAccount(username = "testy", activeCaseloadId = "MDI"), userAccount(username = "testy-1", activeCaseloadId = "LEI")),
                "roles" to listOf(userRole(username = "testy", roleCode = "ROLE_BANANAS"), userRole(username = "testy-1", roleCode = "ROLE_STRAWBERRIES")),
                "accessibleCaseloads" to listOf(
                    accessibleCaseload(username = "testy", caseloadId = "MDI"),
                    accessibleCaseload(username = "testy", caseloadId = "LEI"),
                    accessibleCaseload(username = "testy-1", caseloadId = "MDI"),
                    accessibleCaseload(username = "testy-1", caseloadId = "LEI"),
                )
            )

            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("userMessage")
                .isEqualTo("Validation failure: user.emails[0].createdBy Expected created by, user.emails[0].createdTimestamp Expected created timestamp, user.emails[0].email Expected email address")

        }

        @Test
        fun requestWithIncompleteUserRoleInvalid() {
            val request: Map<String, Any?> = mapOf(
                "user" to migratedUser(),
                "accounts" to listOf(userAccount(username = "testy", activeCaseloadId = "MDI"), userAccount(username = "testy-1", activeCaseloadId = "LEI")),
                "roles" to listOf(
                    mapOf(
                        "createdBy" to "Test",
                    )
                ),
                "accessibleCaseloads" to listOf(
                    accessibleCaseload(username = "testy", caseloadId = "MDI"),
                    accessibleCaseload(username = "testy", caseloadId = "LEI"),
                    accessibleCaseload(username = "testy-1", caseloadId = "MDI"),
                    accessibleCaseload(username = "testy-1", caseloadId = "LEI"),
                )
            )

            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("userMessage")
                .isEqualTo("Validation failure: roles[0].createdTimestamp Expected created timestamp, roles[0].roleCode Expected role code, roles[0].username Expected username")

        }

        @Test
        fun requestWithIncompleteUserAccessibleCaseloadInvalid() {
            val request: Map<String, Any?> = mapOf(
                "user" to migratedUser(),
                "accounts" to listOf(userAccount(username = "testy-1", activeCaseloadId = "MDI"), userAccount(username = "testy-2", activeCaseloadId = "LEI")),
                "roles" to listOf(userRole(username = "testy-1", roleCode = "ROLE_BANANAS"), userRole(username = "testy-2", roleCode = "ROLE_STRAWBERRIES")),
                "accessibleCaseloads" to listOf(
                    mapOf(
                        "createdBy" to "Test",
                    )
                )
            )

            webTestClient.post().uri("/migrate/user")
                .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USERS_API__MIGRATION__RW")))
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("userMessage")
                .isEqualTo("Validation failure: accessibleCaseloads[0].caseloadId Expected caseload id, accessibleCaseloads[0].createdTimestamp Expected created timestamp, accessibleCaseloads[0].username Expected username")

        }

        private fun userAccount(username: String = "test_one", activeCaseloadId: String? = "MDI") = MigratedUserAccount(
            username = username,
            accountType = UsageType.GENERAL,
            accountStatus = AccountStatus.OPEN,
            activeCaseloadId = activeCaseloadId,
            createdTimestamp = LocalDateTime.now(),
            createdBy = "TEST_USER",
        )

        private fun migratedUser() = MigratedUser(
            id = Long.MAX_VALUE,
            emails = listOf(migratedUserEmail("test@email.com"), migratedUserEmail("test-2@email.com")),
            firstName = "Test",
            lastName = "User",
            status = UserStatus.ACTIVE,
            createdTimestamp = LocalDateTime.now(),
            createdBy = "TEST_USER"
        )

        private fun migratedUserEmail(email: String) = MigratedUserEmail(
            email = email,
            createdTimestamp = LocalDateTime.now(),
            createdBy = "TEST_USER"
        )

        private fun accessibleCaseload(username: String, caseloadId: String) = MigratedUserAccessibleCaseload(
            username = username,
            caseloadId = caseloadId,
            createdTimestamp = LocalDateTime.now(),
            createdBy = "TEST_USER"
        )

        private fun userRole(username: String, roleCode: String = "ROLE_TEST") = MigratedUserRole(
            username = username,
            roleCode = roleCode,
            createdTimestamp = LocalDateTime.now(),
            createdBy = "TEST_USER"
        )
    }
}