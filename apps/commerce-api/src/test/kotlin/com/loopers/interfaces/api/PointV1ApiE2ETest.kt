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
import java.math.BigDecimal

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
                    password = "password123",
                    email = "test@example.com",
                    birthDate = "1997-03-25",
                    gender = User.Gender.MALE,
                ),
            )
            val amount = BigDecimal("1000.00")
            pointJpaRepository.save(
                Point.of(
                    userId = user.id,
                    initialBalance = amount,
                ),
            )

            val headers = HttpHeaders().apply {
                set("X-USER-ID", user.username)
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
                { assertThat(response.body?.data?.balance).isEqualTo(amount) },
            )
        }

        @DisplayName("POST /api/v1/points/charge")
        @Nested
        inner class ChargePoint {
            @DisplayName("존재하는 유저가 500원의 잔고에서 1,000원을 충전할 경우, 충전된 보유 총량인 1,500원을 응답으로 반환한다.")
            @Test
            fun returnsChargedAmount_whenUserExists() {
                // arrange
                val initialAmount = BigDecimal("500.00")
                val amount = BigDecimal("1000.00")
                val totalAmount = initialAmount.add(amount)

                val user = userJpaRepository.save(
                    User(
                        username = "testuser",
                        password = "password123",
                        email = "test@example.com",
                        birthDate = "1997-03-25",
                        gender = User.Gender.MALE,
                    ),
                )
                pointJpaRepository.save(Point.of(userId = user.id, initialBalance = initialAmount))

                val request = PointRequest.PointChargeRequestDto(amount = amount)
                val headers = HttpHeaders().apply {
                    set("X-USER-ID", user.username)
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
                    { assertThat(response.body?.data?.balance).isEqualTo(totalAmount) },
                )
            }
        }
    }
}
