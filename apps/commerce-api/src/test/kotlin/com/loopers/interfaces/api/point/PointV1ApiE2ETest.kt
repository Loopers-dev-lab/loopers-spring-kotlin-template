package com.loopers.interfaces.api.point

import com.loopers.domain.point.PointAccount
import com.loopers.domain.point.PointAccountRepository
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PointV1ApiE2ETest @Autowired constructor(
    private val databaseCleanUp: DatabaseCleanUp,
    private val testRestTemplate: TestRestTemplate,
    private val pointAccountRepository: PointAccountRepository,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api/v1/points")
    @Nested
    inner class GetBalance {

        @DisplayName("user id로 포인트 계좌의 잔액 조회를 하면, 보유 총량을 확인할 수 있다.")
        @Test
        fun returnBalance_whenValidUserIdIsProvided() {
            // given
            val pointAccount = createPointAccount()

            // when
            val response = getBalance(
                userId = pointAccount.userId,
            )

            // then
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.balance).isEqualTo(0) },
            )
        }

        @DisplayName("user id 없이 잔액 조회를 하면, 400 Bad Request 응답을 반환한다")
        @Test
        fun throwBadRequest_whenUserIdIsNotProvided() {
            // when
            val response = getBalance(
                userId = null,
            )

            // then
            assertAll(
                { assertThat(response.statusCode.is4xxClientError).isTrue() },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
            )
        }
    }

    @DisplayName("POST /api/v1/points/charge")
    @Nested
    inner class Charge {
        @DisplayName("포인트 계좌에 충전하면, 충전된 보유 총량을 확인할 수 있다.")
        @Test
        fun returnChargedPoint_whenValidAmountIsProvided() {
            // given
            val pointAccount = createPointAccount()

            // when
            val validAmount = 1000

            val response = chargePoint(
                userId = pointAccount.userId,
                amount = validAmount,
            )

            // then
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.balance).isEqualTo(validAmount) },
            )
        }

        @DisplayName("존재하지 않는 유저로 요청하면, 404 Not Found 응답을 확인할 수 있다.")
        @Test
        fun returnNotFound_whenInvalidUserIdIsProvided() {
            // when
            val invalidUserId = 999L

            val response = chargePoint(
                userId = invalidUserId,
            )

            // then
            assertAll(
                { assert(response.statusCode.is4xxClientError) },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
            )
        }
    }

    private fun createPointAccount(
        userId: Long = 1L,
    ): PointAccount {
        return PointAccount.create(userId)
            .let { this.pointAccountRepository.save(it) }
    }

    private fun getBalance(userId: Long?): ResponseEntity<ApiResponse<PointV1Response.GetBalance>> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            userId?.let { set("X-USER-ID", it.toString()) }
        }

        return testRestTemplate.exchange(
            "/api/v1/points",
            HttpMethod.GET,
            HttpEntity(null, headers),
            object : ParameterizedTypeReference<ApiResponse<PointV1Response.GetBalance>>() {},
        )
    }

    private fun chargePoint(
        userId: Long,
        amount: Int = 1000,
    ): ResponseEntity<ApiResponse<PointV1Response.Charge>> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-USER-ID", userId.toString())
        }

        val request = PointV1Request.Charge(amount)

        return testRestTemplate.exchange(
            "/api/v1/points/charge",
            HttpMethod.POST,
            HttpEntity(request, headers),
            object : ParameterizedTypeReference<ApiResponse<PointV1Response.Charge>>() {},
        )
    }
}
