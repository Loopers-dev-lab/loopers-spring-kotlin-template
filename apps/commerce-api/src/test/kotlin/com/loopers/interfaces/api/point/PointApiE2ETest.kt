package com.loopers.interfaces.api.point

import com.loopers.E2ETestSupport
import com.loopers.domain.point.PointFixture
import com.loopers.domain.user.UserFixture
import com.loopers.infrastructure.point.PointJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class PointApiE2ETest(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val pointRepository: PointJpaRepository,
    private val userRepository: UserJpaRepository,
) : E2ETestSupport() {

    companion object {
        private const val ENDPOINT_GET = "/api/v1/points"
        private const val ENDPOINT_POST = "/api/v1/points"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api/v1/points")
    @Nested
    inner class GET {
        @DisplayName("포인트 조회에 성공할 경우, 보유 포인트를 응답으로 반환한다.")
        @Test
        fun returnCurrentPoints_whenIsSuccessful() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val savedBalance = 100L
            val point = PointFixture.create(userId = user.id, balance = savedBalance)
            pointRepository.save(point)

            val requestUrl = ENDPOINT_GET
            val headers = HttpHeaders().apply {
                set("X-USER-ID", user.loginId.value)
            }

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<PointInfoDto.Response>>() {}
            val response = testRestTemplate.exchange(
                requestUrl,
                HttpMethod.GET,
                HttpEntity<Any>(null, headers),
                responseType,
            )
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.balance).isEqualTo(savedBalance) },
            )
        }

        @DisplayName("X-USER-ID 헤더가 없을 경우 400 Bad Request 응답을 반환한다")
        @Test
        fun return400BadRequest_whenXUserIdHeaderIsMissing() {
            // arrange
            val requestUrl = ENDPOINT_GET
            val headers = HttpHeaders()

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<PointInfoDto.Response>>() {}
            val response = testRestTemplate.exchange(
                requestUrl,
                HttpMethod.GET,
                HttpEntity<Any>(headers),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is4xxClientError).isTrue() },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
            )
        }
    }

    @DisplayName("POST /api/v1/points")
    @Nested
    inner class POST {
        @DisplayName("존재하는 유저가 1000원을 충전할 경우, 충전된 보유 총량을 응답으로 반환한다.")
        @Test
        fun returnChargedBalance_whenExistsUserCharged1000() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)
            val point = PointFixture.create(userId = user.id, balance = 1000L)
            pointRepository.save(point)

            val requestUrl = ENDPOINT_POST
            val headers = HttpHeaders().apply {
                set("X-USER-ID", user.loginId.value)
            }
            val chargedBalance = 1000L
            val request = PointChargeDto.Request(chargedBalance)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<PointInfoDto.Response>>() {}
            val response = testRestTemplate.exchange(
                requestUrl,
                HttpMethod.POST,
                HttpEntity<Any>(request, headers),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.balance).isEqualTo(2000L) },
            )
        }

        @DisplayName("존재하지 않는 유저로 요청할 경우, 404 Not Found 응답을 반환한다.")
        @Test
        fun returns404NotFoundError_whenUserIsNotExists() {
            val notExistsLoginId = "sonjs7554"

            val requestUrl = ENDPOINT_POST
            val headers = HttpHeaders().apply {
                set("X-USER-ID", notExistsLoginId)
            }
            val chargedBalance = 1000L
            val request = PointChargeDto.Request(chargedBalance)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<PointInfoDto.Response>>() {}
            val response = testRestTemplate.exchange(
                requestUrl,
                HttpMethod.POST,
                HttpEntity<Any>(request, headers),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is4xxClientError).isTrue() },
            )
        }
    }
}
