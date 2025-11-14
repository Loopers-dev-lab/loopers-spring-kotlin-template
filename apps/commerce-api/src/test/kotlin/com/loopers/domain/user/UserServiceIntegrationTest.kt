package com.loopers.domain.user

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
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean

@SpringBootTest
class UserServiceIntegrationTest @Autowired constructor(
    @MockitoSpyBean
    private val userRepository: UserRepository,
    private val userService: UserService,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("회원가입 시")
    @Nested
    inner class CreateUser {

        @DisplayName("회원 가입시 User 저장이 수행된다.")
        @Test
        fun savesUser_whenUserIsCreated() {
            // arrange
            val username = "testuser"
            val email = "test@example.com"
            val birthDate = "1997-03-25"
            val gender = User.Gender.MALE

            // act
            val result = userService.createUser(
                username = username,
                password = "password123",
                email = email,
                birthDate = birthDate,
                gender = gender,
            )

            // assert
            assertAll(
                { verify(userRepository, times(1)).save(any()) },
                { assertThat(result.username).isEqualTo(username) },
                { assertThat(result.email).isEqualTo(email) },
                { assertThat(result.birthDate).isEqualTo(birthDate) },
                { assertThat(result.gender).isEqualTo(gender) },
            )
        }

        @DisplayName("이미 가입된 ID로 회원가입 시도 시, CONFLICT 에러가 발생한다.")
        @Test
        fun throwsException_whenUsernameAlreadyExists() {
            // arrange
            val duplicateUsername = "duplicate"

            // act
            userService.createUser(
                username = duplicateUsername,
                password = "password123",
                email = "test@example.com",
                birthDate = "1997-03-25",
                gender = User.Gender.MALE,
            )

            val exception = assertThrows<CoreException> {
                userService.createUser(
                    username = duplicateUsername,
                    password = "password456",
                    email = "another@example.com",
                    birthDate = "1990-01-01",
                    gender = User.Gender.FEMALE,
                )
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("회원 정보 조회 시")
    @Nested
    inner class GetUserByUsername {

        @DisplayName("해당 ID의 회원이 존재할 경우, 회원 정보가 반환된다.")
        @Test
        fun returnsUser_whenUsernameExists() {
            // arrange
            val username = "testuser"
            val email = "test@example.com"
            val birthDate = "1997-03-25"
            val gender = User.Gender.MALE

            userService.createUser(
                username = username,
                password = "password123",
                email = email,
                birthDate = birthDate,
                gender = gender,
            )

            // act
            val result = userService.findByUsername(username)

            // assert
            assertAll(
                { assertThat(result).isNotNull },
                { assertThat(result?.username).isEqualTo(username) },
                { assertThat(result?.email).isEqualTo(email) },
                { assertThat(result?.birthDate).isEqualTo(birthDate) },
                { assertThat(result?.gender).isEqualTo(gender) },
            )
        }

        @DisplayName("해당 ID의 회원이 존재하지 않을 경우, null이 반환된다.")
        @Test
        fun returnsNull_whenUsernameDoesNotExist() {
            // arrange
            val username = "nonexistent"

            // act
            val result = userService.findByUsername(username)

            // assert
            assertThat(result).isNull()
        }
    }
}
