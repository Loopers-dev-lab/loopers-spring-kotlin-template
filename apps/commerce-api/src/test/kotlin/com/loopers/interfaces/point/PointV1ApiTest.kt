package com.loopers.interfaces.point

import com.loopers.ApiTest
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.v1.point.PointV1Response
import com.loopers.interfaces.user.UserApiFixture
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

@Import(ApiFixtureConfig::class)
class PointV1ApiTest(
    private val testRestTemplate: TestRestTemplate,
    private val userApiFixture: UserApiFixture,
    private val pointApiFixture: PointApiFixture,
) : ApiTest() {

    companion object {
        const val ENDPOINT = "/api/v1/points"
    }

    @DisplayName("Charge /api/v1/points")
    @Nested
    inner class Charge {
        @Test
        fun `존재하는 유저가 1000원을 충전할 경우, 충전된 보유 총량을 응답으로 반환한다`() {
            // given
            userApiFixture.create()

            // when
            val response = pointApiFixture.charge()

            // then
            assertSoftly { softly ->
                softly.assertThat(response.statusCode.is2xxSuccessful).isTrue
                softly.assertThat(response.body?.data?.userId).isEqualTo("user123")
                softly.assertThat(response.body?.data?.amount).isEqualTo(2000L)
            }
        }

        @Test
        fun `존재하지 않는 유저로 요청할 경우, 404 Not Found 응답을 반환한다`() {
            // when
            val response = pointApiFixture.charge()

            // then
            assertSoftly { softly ->
                { softly.assertThat(response.statusCode.is4xxClientError).isTrue }
            }
        }
    }

    @DisplayName("Get /api/v1/points")
    @Nested
    inner class Get {
        @Test
        fun `해당 ID의 포인트 조회에 성공할 경우, 보유 포인트를 응답으로 반환한다`() {
            // given
            userApiFixture.create()
            pointApiFixture.charge()

            val headers = HttpHeaders()
            headers.set("X-USER-ID", "user123")

            // when
            val responseType = object : ParameterizedTypeReference<ApiResponse<PointV1Response.Get>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                responseType,
            )

            // then
            assertSoftly { softly ->
                softly.assertThat(response.statusCode.is2xxSuccessful).isTrue
                softly.assertThat(response.body?.data?.amount).isEqualTo(2000L)
            }
        }

        @Test
        fun `X-USER-ID 헤더가 없을 경우, 400 Bad Request 응답을 반환한다`() {
            // when
            val responseType = object : ParameterizedTypeReference<ApiResponse<PointV1Response.Charge>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.GET,
                null,
                responseType,
            )

            // then
            assertSoftly { softly ->
                { softly.assertThat(response.statusCode.is4xxClientError).isTrue }
            }
        }
    }
}
