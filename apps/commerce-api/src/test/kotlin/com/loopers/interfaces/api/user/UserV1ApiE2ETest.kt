package com.loopers.interfaces.api.user

import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.user.fixture.UserV1ApiE2EFixture
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
import kotlin.test.assertNotNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private val ENDPOINT_GET_ME = "/api/v1/users/me"
        private val ENDPOINT_SIGN_UP = "/api/v1/users"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    inner class Get {
        @Test
        fun `내 정보 조회에 성공할 경우, 해당하는 유저 정보를 응답으로 반환한다`() {
            // given
            val user = UserV1ApiE2EFixture.saveUser(userRepository)

            val headers = UserV1ApiE2EFixture.getHeaders(user.userName.value)

            // when
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            val response = testRestTemplate.exchange(ENDPOINT_GET_ME, HttpMethod.GET, HttpEntity<Unit>(headers), responseType)

            // then
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.id).isEqualTo(user.id) },
                { assertThat(response.body?.data?.userName).isEqualTo(user.userName.value) },
                { assertThat(response.body?.data?.gender).isEqualTo(user.gender) },
                { assertThat(response.body?.data?.birthDate).isEqualTo(user.birthDate.value) },
                { assertThat(response.body?.data?.email).isEqualTo(user.email.value) },
            )
        }

        @Test
        fun `존재하지 않는 ID 로 조회할 경우, 404 Not Found 응답을 반환한다`() {
            // given
            val headers = UserV1ApiE2EFixture.getHeaders("invalid")

            // when
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            val response = testRestTemplate.exchange(ENDPOINT_GET_ME, HttpMethod.GET, HttpEntity<Unit>(headers), responseType)

            // then
            assertAll(
                { assert(response.statusCode.is4xxClientError) },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
            )
        }
    }

    @Nested
    inner class Post {
        @Test
        fun `회원 가입이 성공할 경우, 생성된 유저 정보를 응답으로 반환한다`() {
            // given
            val headers = UserV1ApiE2EFixture.getHeaders()
            val requestBody = UserV1ApiE2EFixture.givenUserSignUpRequest()
            val requestEntity = HttpEntity(requestBody, headers)

            // when
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            val response = testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, requestEntity, responseType)

            // then
            assertAll(
                { assert(response.statusCode.is2xxSuccessful) },
                { assertNotNull(response.body?.data) },
                { assertThat(response.body?.data?.userName).isEqualTo(requestBody.userName) },
                { assertThat(response.body?.data?.gender).isEqualTo(requestBody.gender) },
                { assertThat(response.body?.data?.birthDate).isEqualTo(requestBody.birthDate) },
                { assertThat(response.body?.data?.email).isEqualTo(requestBody.email) },
            )
        }

        @Test
        fun `회원 가입 시에 성별이 없을 경우, 400 Bad Request 응답을 반환한다`() {
            // given
            val headers = UserV1ApiE2EFixture.getHeaders()
            val requestBody = """
                {
                    "userId": "testUser",
                    "birthDate": "1990-01-01",
                    "email": "email@example.com"
                }
            """.trimIndent()
            val requestEntity = HttpEntity(requestBody, headers)

            // when
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            val response = testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, requestEntity, responseType)

            // then
            assertAll(
                { assert(response.statusCode.is4xxClientError) },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
            )
        }
    }
}
