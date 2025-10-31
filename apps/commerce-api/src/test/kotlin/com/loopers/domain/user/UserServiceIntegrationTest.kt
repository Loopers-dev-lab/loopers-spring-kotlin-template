package com.loopers.domain.user

import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class UserServiceIntegrationTest @Autowired constructor(
    private val userService: UserService,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun cleanUp() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("회원 가입시, ")
    @Nested
    inner class RegisterUser {
        @DisplayName("User 저장이 수행된다.")
        @Test
        fun saveUser_whenRegisterUser() {
            // arrange

            // act
            val result = userService.registerUser(userId = "testId", email = "test@test.com", birth = "2025-10-25", gender = Gender.OTHER)

            // assert
            val user = userJpaRepository.findById(result.id).get()
            assertThat(user).isNotNull()
        }

        @DisplayName("이미 가입된 ID로 시도하면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsException_whenConflictIdIsProvided() {
            // arrange
            val user = userService.registerUser(userId = "testId", email = "test@test.com", birth = "2025-10-25", gender = Gender.OTHER)

            // act
            val exception = assertThrows<CoreException> {
                userService.registerUser(user.userId, user.email, user.birth, user.gender)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("회원 정보 조회시, ")
    @Nested
    inner class GetUser {
        @DisplayName("해당 ID의 회원이 존재할 경우, 회원 정보가 반환된다.")
        @Test
        fun getUser_whenUserIdExists() {
            // arrange
            val user = userJpaRepository.save(User(userId = "testId", email = "test@test.com", birth = "2025-10-25", gender = Gender.OTHER))

            // act
            val result = userService.getUserByUserId(user.userId)

            // assert
            assertAll(
                { assertThat(result).isNotNull() },
                { assertThat(result?.userId).isEqualTo(user.userId) },
                { assertThat(result?.email).isEqualTo(user.email) },
                { assertThat(result?.birth).isEqualTo(user.birth) },
                { assertThat(result?.gender).isEqualTo(user.gender) },
            )
        }

        @DisplayName("해당 ID의 회원이 존재하지 않을 경우, Null이 반환된다.")
        @Test
        fun returnNull_whenUserIdNotExists() {
            // arrange
            val userId = "testId"

            // act
            val result = userService.getUserByUserId(userId)

            // assert
            assertThat(result).isNull()
        }
    }
}
