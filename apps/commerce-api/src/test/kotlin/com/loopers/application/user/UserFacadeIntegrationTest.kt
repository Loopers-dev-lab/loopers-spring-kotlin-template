package com.loopers.application.user

import com.loopers.application.user.fixture.UserFacadeIntegrationFixture
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class UserFacadeIntegrationTest @Autowired constructor(
    private val userFacade: UserFacade,
    private val userRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("회원가입")
    @Nested
    inner class SignUp {
        @Test
        fun `회원 가입 시 user 정보를 저장한다`() {
            // when
            UserFacadeIntegrationFixture.signUp(userFacade)

            // then
            val user = userRepository.findAll()
            assertThat(user.size).isEqualTo(1)
        }

        @Test
        fun `이미 가입된 ID 로 회원가입 시도 시 실패한다`() {
            // given
            val userInfo = UserFacadeIntegrationFixture.givenSignUp()
            userFacade.signUp(userInfo)

            // when
            val exception = assertThrows<CoreException> {
                userFacade.signUp(userInfo)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }
}
