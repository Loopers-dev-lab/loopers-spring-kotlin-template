package com.loopers.interfaces.api

import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.user.UserV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT_REGISTER = "/api/v1/users/register"
        private val ENDPOINT_GET: (String) -> String = { userId: String -> "/api/v1/users/$userId" }
    }

    @AfterEach
    fun cleanUp() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/users/register")
    @Nested
    inner class RegisterUser {
        @DisplayName("회원 가입이 성공할 경우, 생성된 유저 정보를 응답으로 반환한다.")
        @Test
        fun returnsUserInfo_whenRegisterUser() {
            // arrange
            val req = UserV1Dto.RegisterUserRequest(
                userId = "testId",
                email = "test@test.com",
                birth = "2025-10-25",
                gender = Gender.OTHER,
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            val res = testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, HttpEntity(req), responseType)

            // assert
            assertAll(
                { assertThat(res.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(res.body?.data?.userId).isEqualTo(req.userId) },
                { assertThat(res.body?.data?.email).isEqualTo(req.email) },
                { assertThat(res.body?.data?.birth).isEqualTo(req.birth) },
                { assertThat(res.body?.data?.gender).isEqualTo(req.gender) },
            )
        }

        @DisplayName("회원 가입 시에 성별이 없을 경우, 400 Bad Request 응답을 반환한다.")
        @Test
        fun throwsBadRequest_whenGenderIsNotProvided() {
            // arrange
            val req = UserV1Dto.RegisterUserRequest(
                userId = "testId",
                email = "test@test.com",
                birth = "2025-10-25",
                gender = Gender.NONE,
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            val res = testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, HttpEntity(req), responseType)

            // assert
            assertAll(
                { assertThat(res.statusCode.is4xxClientError).isTrue },
                { assertThat(res.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
            )
        }
    }

    @DisplayName("POST /api/v1/users/{userId}")
    @Nested
    inner class GetUser {
        @DisplayName("존재하는 회원의 ID를 주면, 생성된 유저 정보를 응답으로 반환한다.")
        @Test
        fun returnsUserInfo_whenUserIdExists() {
            // arrange
            val user = userJpaRepository.save(User(userId = "testId", email = "test@test.com", birth = "2025-10-25", gender = Gender.OTHER))

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            val res = testRestTemplate.exchange(ENDPOINT_GET(user.userId), HttpMethod.GET, HttpEntity<Any>(Unit), responseType)

            // assert
            assertAll(
                { assertThat(res.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(res.body?.data?.userId).isEqualTo(user.userId) },
                { assertThat(res.body?.data?.email).isEqualTo(user.email) },
                { assertThat(res.body?.data?.birth).isEqualTo(user.birth) },
                { assertThat(res.body?.data?.gender).isEqualTo(user.gender) },
            )
        }

        @DisplayName("존재하지 않는 ID로 조회할 경우, 404 Not Found 응답을 반환한다.")
        @Test
        fun throwsNotFound_whenUserIdNotExists() {
            // arrange
            val userId = "testId"

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            val res = testRestTemplate.exchange(ENDPOINT_GET(userId), HttpMethod.GET, HttpEntity<Any>(Unit), responseType)

            // assert
            assertAll(
                { assertThat(res.statusCode.is4xxClientError).isTrue },
                { assertThat(res.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
            )
        }
    }
}
