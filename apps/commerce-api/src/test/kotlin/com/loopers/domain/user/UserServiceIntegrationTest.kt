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
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("회원 가입시,")
    @Nested
    inner class RegisterUser {
        @DisplayName("User 저장이 수행된다.")
        @Test
        fun savesUser_whenRegisteringUser() {
            // arrange
            val userId = "testuser"

            // act
            userService.registerUser(
                userId = userId,
                email = "test@example.com",
                birthDate = "1990-01-01",
                gender = Gender.MALE,
            )

            // assert
            val savedUser = userJpaRepository.findByUserId(userId)
            assertAll(
                { assertThat(savedUser).isNotNull() },
                { assertThat(savedUser?.userId).isEqualTo(userId) },
                { assertThat(savedUser?.email).isEqualTo("test@example.com") },
                { assertThat(savedUser?.birthDate).isEqualTo("1990-01-01") },
                { assertThat(savedUser?.gender).isEqualTo(Gender.MALE) },
            )
        }

        @DisplayName("이미 가입된 ID로 회원가입 시도 시, 실패한다.")
        @Test
        fun failsToRegisterUser_whenUserIdAlreadyExists() {
            // arrange
            val userId = "testuser"
            userJpaRepository.save(
                User(
                    userId = userId,
                    email = "existing@example.com",
                    birthDate = "1990-01-01",
                    gender = Gender.MALE,
                ),
            )

            // act
            val exception = assertThrows<CoreException> {
                userService.registerUser(
                    userId = userId,
                    email = "new@example.com",
                    birthDate = "1991-02-02",
                    gender = Gender.FEMALE,
                )
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("내 정보 조회시,")
    @Nested
    inner class GetUserByUserId {
        @DisplayName("해당 ID의 회원이 존재할 경우, 회원 정보가 반환된다.")
        @Test
        fun returnsUser_whenUserExists() {
            // arrange
            val userId = "testuser"
            val savedUser = userJpaRepository.save(
                User(
                    userId = userId,
                    email = "test@example.com",
                    birthDate = "1990-01-01",
                    gender = Gender.MALE,
                ),
            )

            // act
            val result = userService.getUserByUserId(userId)

            // assert
            assertAll(
                { assertThat(result).isNotNull() },
                { assertThat(result?.id).isEqualTo(savedUser.id) },
                { assertThat(result?.userId).isEqualTo(userId) },
                { assertThat(result?.email).isEqualTo("test@example.com") },
            )
        }

        @DisplayName("해당 ID의 회원이 존재하지 않을 경우, null이 반환된다.")
        @Test
        fun returnsNull_whenUserDoesNotExist() {
            // arrange
            val nonExistentUserId = "nonexistent"

            // act
            val result = userService.getUserByUserId(nonExistentUserId)

            // assert
            assertThat(result).isNull()
        }
    }

    @DisplayName("포인트 조회시,")
    @Nested
    inner class GetPointByUserId {
        @DisplayName("해당 ID의 회원이 존재할 경우, 보유 포인트가 반환된다.")
        @Test
        fun returnsPoint_whenUserExists() {
            // arrange
            val userId = "testuser"
            userJpaRepository.save(
                User(
                    userId = userId,
                    email = "test@example.com",
                    birthDate = "1990-01-01",
                    gender = Gender.MALE,
                ),
            )

            // act
            val result = userService.getPointByUserId(userId)

            // assert
            assertAll(
                { assertThat(result).isNotNull() },
                { assertThat(result).isEqualTo(0L) },
            )
        }

        @DisplayName("해당 ID의 회원이 존재하지 않을 경우, null이 반환된다.")
        @Test
        fun returnsNull_whenUserDoesNotExist() {
            // arrange
            val nonExistentUserId = "nonexistent"

            // act
            val result = userService.getPointByUserId(nonExistentUserId)

            // assert
            assertThat(result).isNull()
        }
    }

    @DisplayName("포인트 충전시,")
    @Nested
    inner class ChargePoint {
        @DisplayName("존재하지 않는 유저 ID로 충전을 시도한 경우, 실패한다.")
        @Test
        fun failsToChargePoint_whenUserDoesNotExist() {
            // arrange
            val nonExistentUserId = "nonexistent"

            // act
            val exception = assertThrows<CoreException> {
                userService.chargePoint(nonExistentUserId, 1000L)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
