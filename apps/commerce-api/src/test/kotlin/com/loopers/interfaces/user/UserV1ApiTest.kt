package com.loopers.interfaces.user

import com.loopers.ApiTest
import com.loopers.domain.user.Gender
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.v1.user.UserV1Request
import com.loopers.interfaces.api.v1.user.UserV1Response
import com.loopers.support.config.ApiFixtureConfig
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType

@Import(ApiFixtureConfig::class)
class UserV1ApiTest(
    private val testRestTemplate: TestRestTemplate,
    private val userApiFixture: UserApiFixture,
) : ApiTest() {

    companion object {
        const val ENDPOINT_POST = "/api/v1/users"
        const val ENDPOINT_GET_ME = "/api/v1/users/me"
    }

    @DisplayName("SingUp /api/v1/users")
    @Nested
    inner class SignUp {
        @Test
        fun `회원 가입이 성공할 경우, 생성된 유저 정보를 응답으로 반환한다`() {
            // given & when
            val response = userApiFixture.create()

            // then
            assertSoftly { softly ->
                softly.assertThat(response!!.statusCode.is2xxSuccessful).isTrue
                softly.assertThat(response.body?.data?.userId).isEqualTo("user123")
                softly.assertThat(response.body?.data?.email).isEqualTo("test@example.com")
                softly.assertThat(response.body?.data?.birthDate).isEqualTo("2000-01-01")
                softly.assertThat(response.body?.data?.gender).isEqualTo(Gender.MALE)
            }
        }

        @Test
        fun `회원 가입 시에 성별이 없을 경우, 400 Bad Request 응답을 반환한다`() {
            // given
            val request = """
                {
                    "userId": "1234",
                    "email": "email@naver.com",
                    "birthDate": "2025-07-14"
                }
            """.trimIndent()
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            HttpEntity(request, headers)

            // when
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Response.SignUp>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_POST,
                HttpMethod.POST,
                HttpEntity(request),
                responseType,
            )

            // then
            assertSoftly { softly ->
                { softly.assertThat(response.statusCode.is4xxClientError).isTrue }
            }
        }
    }

    @DisplayName("GetMyInfo /api/v1/users/me")
    @Nested
    inner class GetMyInfo {
        @Test
        fun `내 정보 조회에 성공할 경우, 해당하는 유저 정보를 응답으로 반환한다`() {
            // given
            val userSignUpRequest = UserV1Request.SignUp(
                userId = "user123",
                email = "test@example.com",
                birthDate = "2000-01-01",
                gender = Gender.MALE,
            )

            val userSignUpResponseType = object : ParameterizedTypeReference<ApiResponse<UserV1Response.SignUp>>() {}
            testRestTemplate.exchange(
                ENDPOINT_POST,
                HttpMethod.POST,
                HttpEntity(userSignUpRequest),
                userSignUpResponseType,
            )

            val headers = HttpHeaders()
            headers.set("X-USER-ID", "user123")

            // when
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Response.SignUp>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_GET_ME,
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                responseType,
            )

            // then
            assertSoftly { softly ->
                softly.assertThat(response.statusCode.is2xxSuccessful).isTrue
                softly.assertThat(response.body?.data?.userId).isEqualTo("user123")
                softly.assertThat(response.body?.data?.email).isEqualTo("test@example.com")
                softly.assertThat(response.body?.data?.birthDate).isEqualTo("2000-01-01")
                softly.assertThat(response.body?.data?.gender).isEqualTo(Gender.MALE)
            }
        }

        @Test
        fun `존재하지 않는 ID 로 조회할 경우, 404 Not Found 응답을 반환한다`() {
            // given
            val headers = HttpHeaders()
            headers.set("X-USER-ID", "user123")

            // when
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Response.SignUp>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_GET_ME,
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                responseType,
            )

            // then
            assertSoftly { softly ->
                { softly.assertThat(response.statusCode.is4xxClientError).isTrue }
            }
        }
    }
}
