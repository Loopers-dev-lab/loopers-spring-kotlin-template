package com.loopers.interfaces.api

import com.loopers.domain.point.Point
import com.loopers.domain.user.User
import com.loopers.infrastructure.point.PointJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.point.PointRequest
import com.loopers.interfaces.api.point.PointResponse
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PointV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userJpaRepository: UserJpaRepository,
    private val pointJpaRepository: PointJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT_GET = "/api/v1/points"
        private const val ENDPOINT_CHARGE = "/api/v1/points/charge"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api/v1/points")
    @Nested
    inner class GetPoint {
        @DisplayName("포인트 조회에 성공할 경우, 보유 포인트를 응답으로 반환한다.")
        @Test
        fun returnsPoint_whenUserHasPoint() {
            // arrange
            val user = userJpaRepository.save(
                User(
                    username = "testuser",
                    email = "test@example.com",
                    birthDate = "1997-03-25",
                    gender = User.Gender.MALE,
                ),
            )
            val amount = 1000L
            pointJpaRepository.save(
                Point.of(
                    userId = user.id,
                    initialAmount = amount,
                ),
            )

            val headers = HttpHeaders().apply {
                set("X-USER-ID", user.id.toString())
            }

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<PointResponse.PointResponseDto>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_GET,
                HttpMethod.GET,
                HttpEntity<Any>(null, headers),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.amount).isEqualTo(amount) },
            )
        }

        @DisplayName("X-USER-ID 헤더가 없을 경우, 400 Bad Request 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whenHeaderIsMissing() {
            // arrange
            val headers = HttpHeaders()

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<PointResponse.PointResponseDto>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_GET,
                HttpMethod.GET,
                HttpEntity<Any>(null, headers),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is4xxClientError).isTrue() },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
            )
        }
    }

    @DisplayName("POST /api/v1/points/charge")
    @Nested
    inner class ChargePoint {
        @DisplayName("존재하는 유저가 500원의 잔고에서 1,000원을 충전할 경우, 충전된 보유 총량인 1,500원을 응답으로 반환한다.")
        @Test
        fun returnsChargedAmount_whenUserExists() {
            // arrange
            val initialAmount = 500L
            val amount = 1000L
            val totalAmount = initialAmount + amount

            val user = userJpaRepository.save(
                User(
                    username = "testuser",
                    email = "test@example.com",
                    birthDate = "1997-03-25",
                    gender = User.Gender.MALE,
                ),
            )
            pointJpaRepository.save(Point.of(userId = user.id, initialAmount = initialAmount))

            val request = PointRequest.PointChargeRequestDto(amount = amount)
            val headers = HttpHeaders().apply {
                set("X-USER-ID", user.id.toString())
            }

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<PointResponse.PointResponseDto>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_CHARGE,
                HttpMethod.POST,
                HttpEntity(request, headers),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.amount).isEqualTo(totalAmount) },
            )
        }

        @DisplayName("존재하지 않는 유저로 요청할 경우, 404 Not Found 응답을 반환한다.")
        @Test
        fun returnsNotFound_whenUserDoesNotExist() {
            // arrange
            val request = PointRequest.PointChargeRequestDto(amount = 1000L)
            val headers = HttpHeaders().apply {
                set("X-USER-ID", "999")
            }

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<PointResponse.PointResponseDto>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_CHARGE,
                HttpMethod.POST,
                HttpEntity(request, headers),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is4xxClientError).isTrue() },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
            )
        }
    }
}
