package com.loopers.interfaces.api.user

import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import com.loopers.interfaces.api.ApiResponse
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
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val userRepository: UserRepository,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api/v1/users/{userId}")
    @Nested
    inner class GetByID {
        @DisplayName("숫자가 아닌 ID 로 요청하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun throwsBadRequest_whenIdIsNotProvided() {
            // when
            val response = getUserBy("나나")

            // then
            assertAll(
                { assertThat(response.statusCode.is4xxClientError).isTrue },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
            )
        }

        @DisplayName("존재하지 않는 예시 ID를 주면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun throwsException_whenInvalidIdIsProvided() {
            // given
            val invalidId = -1L

            // when
            val response = getUserBy(invalidId)

            // then
            assertAll(
                { assert(response.statusCode.is4xxClientError) },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
            )
        }

        @DisplayName("내 정보 조회하면, 해당하는 유저 정보를 응답으로 반환한다.")
        @Test
        fun returnUserResponse_whenAlreadyExistIdIsProvided() {
            // given
            val existUser = createUser()

            // when
            val response = getUserBy(existUser.id)

            // then
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data).isNotNull() },
                { assertThat(response.body?.data?.id).isEqualTo(existUser.id) },
            )
        }
    }

    @DisplayName("POST /api/v1/users/sign-up")
    @Nested
    inner class SignUp {
        @DisplayName("적절한 값으로 회원가입할 수 있다.")
        @Test
        fun signUpUser_whenValidRequestIsProvided() {
            // when
            val request = createSignUpRequest()
            val response = signUp(request)

            // then
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data).isNotNull() },
                { assertThat(response.body?.data?.id).isNotNull() },
                { assertThat(response.body?.data?.username).isEqualTo(request.username) },
                { assertThat(response.body?.data?.birth).isEqualTo(request.birth) },
                { assertThat(response.body?.data?.gender).isEqualTo(request.gender) },
                { assertThat(response.body?.data?.email).isEqualTo(request.email) },
            )
        }

        @DisplayName("성별 정보 없이 회원가입하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun signUpUser_whenGenderIsMissing_thenReturnBadRequest() {
            // when
            val invalidJson = """
                {
                    "username": "testuser",
                    "birth": "2000-01-01",
                    "email": "test@toong.io"
                }
            """.trimIndent()
            val response = signUpWithRawJson(invalidJson)

            // then
            assertAll(
                { assertThat(response.statusCode.is4xxClientError).isTrue },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
            )
        }

        @DisplayName("이미 존재하는 username로 회원가입하면, 409 CONFLICT 응답을 받는다.")
        @Test
        fun signUpUser_whenUsernameAlreadyExists_thenReturnConflict() {
            // given
            val existUsername = "username"
            val existUser = createUser(
                username = existUsername,
            )

            // when
            val request = createSignUpRequest(username = existUsername)
            val response = signUp(request)

            // then
            assertAll(
                { assertThat(response.statusCode.is4xxClientError).isTrue },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
            )
        }
    }

    private fun getUserBy(userId: Long): ResponseEntity<ApiResponse<UserV1Response.GetUserById>> {
        return testRestTemplate.exchange(
            "/api/v1/users/$userId",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<UserV1Response.GetUserById>>() {},
        )
    }

    private fun getUserBy(userId: String): ResponseEntity<ApiResponse<UserV1Response.GetUserById>> {
        return testRestTemplate.exchange(
            "/api/v1/users/$userId",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<UserV1Response.GetUserById>>() {},
        )
    }

    private fun createSignUpRequest(
        username: String = "username",
        birth: String = "2000-01-01",
        gender: Gender = Gender.MALE,
        email: String = "test@example.com",
    ): UserV1Request.SignUp {
        return UserV1Request.SignUp(username, birth, email, gender)
    }

    private fun signUpWithRawJson(json: String): ResponseEntity<ApiResponse<UserV1Response.SignUp>> {
        val httpHeaders = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        return testRestTemplate.exchange(
            "/api/v1/users/sign-up",
            HttpMethod.POST,
            HttpEntity(json, httpHeaders),
            object : ParameterizedTypeReference<ApiResponse<UserV1Response.SignUp>>() {},
        )
    }

    private fun signUp(request: UserV1Request.SignUp): ResponseEntity<ApiResponse<UserV1Response.SignUp>> {
        val httpHeaders = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        return testRestTemplate.exchange(
            "/api/v1/users/sign-up",
            HttpMethod.POST,
            HttpEntity(request, httpHeaders),
            object : ParameterizedTypeReference<ApiResponse<UserV1Response.SignUp>>() {},
        )
    }

    private fun createUser(
        username: String = "username",
        birth: LocalDate = LocalDate.of(2000, 1, 1),
        gender: Gender = Gender.MALE,
        email: String = "test@example.com",
    ): User {
        val user = User.of(
            username = username,
            birth = birth,
            gender = gender,
            email = email,

            )
        return userRepository.save(user)
    }
}
