package com.loopers.domain.user

import com.loopers.IntegrationTestSupport
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.springframework.test.util.ReflectionTestUtils
import kotlin.test.Test

class UserServiceTest(
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) : IntegrationTestSupport() {

    @AfterEach
    fun tearDown() {
        ReflectionTestUtils.setField(userService, "userRepository", userRepository)
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("회원 가입 시 ")
    @Nested
    inner class RegisterUser {

        @DisplayName("User 저장이 수행된다. ( 중복 체크 Spy 처리 )")
        @Test
        fun registerUser_withSpyRepository() {
            // arrange
            val user = UserFixture.create()

            val spy = Mockito.spy(userRepository)
            ReflectionTestUtils.setField(userService, "userRepository", spy)
            val savedUser = userService.registerUser(user)

            // assert
            assertAll(
                { assertThat(savedUser.loginId.value).isEqualTo(user.loginId.value) },
                { verify(spy, times(1)).save(any()) },
            )
        }

        @DisplayName("이미 가입된 ID 로 회원가입 시도 시, 실패한다.")
        @Test
        fun registerUserWithDuplicateLoginId() {
            // arrange
            val user = UserFixture.create()
            val duplicatedIdUser = UserFixture.create(loginId = user.loginId.value)

            // act
            userService.registerUser(user)

            // assert
            val exception = assertThrows<CoreException> { userService.registerUser(duplicatedIdUser) }
            assertThat(exception.message).isEqualTo("이미 존재하는 아이디입니다.")
        }
    }

    @DisplayName("회원 정보 조회 시")
    @Nested
    inner class GetUser {
        @DisplayName("해당 ID 의 회원이 존재 할 경우, 회원 정보가 반환된다.")
        @Test
        fun returnUserInfo_whenUserExists() {
            // arrange
            val userModel = UserFixture.create(gender = "FEMALE")
            val savedUser = userService.registerUser(userModel)

            // act
            val userInfo = userService.getUser(savedUser.loginId.value)

            // assert
            assertAll(
                { assertThat(userInfo?.loginId?.value).isEqualTo(savedUser.loginId.value) },
                { assertThat(userInfo?.email?.value).isEqualTo(savedUser.email.value) },
                { assertThat(userInfo?.birthDate?.value).isEqualTo(savedUser.birthDate.value) },
                { assertThat(userInfo?.gender?.name).isEqualTo(savedUser.gender.name) },
            )
        }

        @DisplayName("해당 ID 의 회원이 존재 하지 않을 경우, null 이 반환된다")
        @Test
        fun returnNull_whenUserNotExists() {
            // arrange
            val notExistLoginId = "notExist12"
            // act
            val userInfo = userService.getUser(notExistLoginId)

            // assert
            assertThat(userInfo).isNull()
        }
    }
}
