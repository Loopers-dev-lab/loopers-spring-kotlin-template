package com.loopers.interfaces.api

import com.loopers.domain.user.User
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.user.UserRequest
import com.loopers.interfaces.api.user.UserResponse
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
        private const val ENDPOINT_CREATE = "/api/v1/users"
        private val ENDPOINT_GET: (String) -> String = { username: String -> "/api/v1/users?username=$username" }
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    inner class CreateUser {
        @DisplayName("회원 가입이 성공할 경우, 생성된 유저 정보를 응답으로 반환한다.")
        @Test
        fun returnsUserInfo_whenSignupSucceeds() {
            // arrange
            val request = UserRequest.UserCreateRequestDto(
                username = "testuser",
                password = "password123",
                email = "test@example.com",
                birthDate = "1997-03-25",
                gender = User.Gender.MALE,
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserResponse.UserResponseDto>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_CREATE,
                HttpMethod.POST,
                HttpEntity(request),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body.data?.username).isEqualTo("testuser") },
                { assertThat(response.body.data?.email).isEqualTo("test@example.com") },
                { assertThat(response.body.data?.birthDate).isEqualTo("1997-03-25") },
                { assertThat(response.body.data?.gender).isEqualTo(User.Gender.MALE) },
            )
        }

        @DisplayName("회원 가입 시에 성별이 없을 경우, 400 Bad Request 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whenGenderIsMissing() {
            // arrange
            val requestBody = mapOf(
                "username" to "testuser",
                "email" to "test@example.com",
                "birthDate" to "1997-03-25",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserResponse.UserResponseDto>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_CREATE,
                HttpMethod.POST,
                HttpEntity(requestBody),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is4xxClientError).isTrue() },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
            )
        }
    }

    @DisplayName("GET /api/v1/users")
    @Nested
    inner class GetUser {
        @DisplayName("내 정보 조회에 성공할 경우, 해당하는 유저 정보를 응답으로 반환한다.")
        @Test
        fun returnsUserInfo_whenUsernameExists() {
            // arrange
            val user = userJpaRepository.save(
                User(
                    username = "testuser",
                    password = "password123",
                    email = "test@example.com",
                    birthDate = "1997-03-25",
                    gender = User.Gender.MALE,
                ),
            )
            val requestUrl = ENDPOINT_GET(user.username)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserResponse.UserResponseDto>>() {}
            val response = testRestTemplate.exchange(requestUrl, HttpMethod.GET, HttpEntity<Any>(Unit), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.username).isEqualTo("testuser") },
                { assertThat(response.body?.data?.email).isEqualTo("test@example.com") },
                { assertThat(response.body?.data?.birthDate).isEqualTo("1997-03-25") },
                { assertThat(response.body?.data?.gender).isEqualTo(User.Gender.MALE) },
            )
        }

        @DisplayName("존재하지 않는 ID로 조회할 경우, 404 Not Found 응답을 반환한다.")
        @Test
        fun returnsNotFound_whenUsernameDoesNotExist() {
            // arrange
            val nonExistentUsername = "nonexistent"
            val requestUrl = ENDPOINT_GET(nonExistentUsername)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserResponse.UserResponseDto>>() {}
            val response = testRestTemplate.exchange(requestUrl, HttpMethod.GET, HttpEntity<Any>(Unit), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode.is4xxClientError).isTrue() },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
            )
        }
    }
}
