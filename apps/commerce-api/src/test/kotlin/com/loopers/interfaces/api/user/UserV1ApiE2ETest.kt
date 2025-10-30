package com.loopers.interfaces.api.user

import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.test.KSelect
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.instancio.Instancio
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val userRepository: UserRepository,
) {

    companion object {
        private val ENDPOINT_SIGNUP: () -> String = { "/api/v1/users/sign-up" }
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/users/sign-up")
    @Nested
    inner class SignUp {
        val correctUsername = "username"
        val correctBirth = "2000-01-01"
        val correctGender = Gender.MALE
        val correctEmail = "to323ong@toong.io"

        @DisplayName("적절한 값으로 회원가입을 요청하는 경우 회원가입에 성공한다.")
        @Test
        fun signUpUser_whenValidRequestIsProvided() {
            // when
            val request = UserV1Request.SignUp(
                username = correctUsername,
                birth = correctBirth,
                gender = correctGender,
                email = correctEmail,
            )
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }
            val httpEntity = HttpEntity(request, headers)

            val requestUrl = ENDPOINT_SIGNUP()
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Response.SignUp>>() {}
            val response = testRestTemplate.exchange(
                requestUrl,
                HttpMethod.POST,
                httpEntity,
                responseType,
            )

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

        @DisplayName("성별 정보 없이 회원가입을 요청하는 경우 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun signUpUser_whenGenderIsMissing_thenReturnBadRequest() {
            // when
            val requestJson = """
                {
                    "username": {$correctUsername},
                    "birth": {$correctBirth},
                    "email": {$correctEmail}},
                }
            """.trimIndent()

            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }
            val httpEntity = HttpEntity(requestJson, headers)
            val requestUrl = ENDPOINT_SIGNUP()

            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Response.SignUp>>() {}
            val response = testRestTemplate.exchange(
                requestUrl,
                HttpMethod.POST,
                httpEntity,
                responseType,
            )

            // then
            assertAll(
                { assertThat(response.statusCode.is4xxClientError).isTrue },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
            )
        }

        @DisplayName("이미 존재하는 username로 회원가입을 요청하는 경우 409 CONFLICT 응답을 받는다.")
        @Test
        fun signUpUser_whenUsernameAlreadyExists_thenReturnConflict() {
            // given
            val existingUsername = "existingUsername"
            val existingUser = Instancio.of(User::class.java)
                .ignore(KSelect.field(User::id))
                .set(KSelect.field(User::username), existingUsername)
                .create()
            userRepository.save(existingUser)

            // when
            val request = UserV1Request.SignUp(
                username = existingUsername,
                birth = correctBirth,
                gender = correctGender,
                email = correctEmail,
            )
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }
            val httpEntity = HttpEntity(request, headers)
            val requestUrl = ENDPOINT_SIGNUP()
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Response.SignUp>>() {}
            val response = testRestTemplate.exchange(
                requestUrl,
                HttpMethod.POST,
                httpEntity,
                responseType,
            )

            // then
            assertAll(
                { assertThat(response.statusCode.is4xxClientError).isTrue },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
            )
        }
    }
}
