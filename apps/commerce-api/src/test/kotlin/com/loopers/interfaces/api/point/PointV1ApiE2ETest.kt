package com.loopers.interfaces.api.point

import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.test.KSelect
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.instancio.Instancio
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PointV1ApiE2ETest @Autowired constructor(
    private val databaseCleanUp: DatabaseCleanUp,
    private val testRestTemplate: TestRestTemplate,
    private val userRepository: UserRepository,
) {

    companion object {
        private val ENDPOINT_CHARGE: () -> String = { "/api/v1/points/charge" }
        private val ENDPOINT_GET_BALANCE: () -> String = { "/api/v1/points/balance" }
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api/v1/points/balance")
    @Nested
    inner class GetBalance {
        var user: User? = null

        @BeforeEach
        fun setUp() {
            val mockUser = Instancio.of(User::class.java)
                .ignore(KSelect.field(User::id))
                .set(KSelect.field(User::username), "김준형")
                .set(KSelect.field(User::birth), LocalDate.of(1994, 9, 23))
                .set(KSelect.field(User::email), "toong@toong.io")
                .set(KSelect.field(User::gender), Gender.MALE)
                .create()
            user = userRepository.save(mockUser)
        }

        @DisplayName("존재하는 유저로 잔액 조회를 하는 경우 보유 총량을 확인할 수 있다.")
        @Test
        fun returnBalance_whenValidUserIdIsProvided() {
            // given
            val existUser = user!!

            // when
            val requestUrl = ENDPOINT_GET_BALANCE()
            val responseType = object : ParameterizedTypeReference<ApiResponse<PointV1Response.GetBalance>>() {}
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("X-USER-ID", existUser.id.toString())
            }
            val httpEntity = HttpEntity(null, headers)
            val response = testRestTemplate.exchange(
                requestUrl,
                HttpMethod.GET,
                httpEntity,
                responseType,
            )

            // then
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.balance).isEqualTo(0) },
            )
        }

        @DisplayName("user id값이 포함되지 않는 경우 400 Bad Request 응답을 반환한다")
        @Test
        fun throwBadRequest_whenUserIdIsNotProvided() {
            // when
            val requestUrl = ENDPOINT_GET_BALANCE()
            val responseType = object : ParameterizedTypeReference<ApiResponse<PointV1Response.GetBalance>>() {}
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }
            val httpEntity = HttpEntity(null, headers)
            val response = testRestTemplate.exchange(
                requestUrl,
                HttpMethod.GET,
                httpEntity,
                responseType,
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
        var user: User? = null

        @BeforeEach
        fun setUp() {
            val mockUser = Instancio.of(User::class.java)
                .ignore(KSelect.field(User::id))
                .set(KSelect.field(User::username), "김준형")
                .set(KSelect.field(User::birth), LocalDate.of(1994, 9, 23))
                .set(KSelect.field(User::email), "toong@toong.io")
                .set(KSelect.field(User::gender), Gender.MALE)
                .create()
            user = userRepository.save(mockUser)
        }

        @DisplayName("존재하는 유저가 1000원을 충전하는 경우 충전된 보유 총량을 확인할 수 있다.")
        @Test
        fun returnChargedPoint_whenValidAmountIsProvided() {
            // given
            val existUser = user!!

            // when
            val requestUrl = ENDPOINT_CHARGE()
            val responseType = object : ParameterizedTypeReference<ApiResponse<PointV1Response.Charge>>() {}

            val correctAmount = 1000
            val request = PointV1Request.Charge(
                amount = correctAmount,
            )
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("X-USER-ID", existUser.id.toString())
            }
            val httpEntity = HttpEntity(request, headers)
            val response = testRestTemplate.exchange(
                requestUrl,
                HttpMethod.POST,
                httpEntity,
                responseType,
            )

            // then
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.balance).isEqualTo(correctAmount) },
            )
        }

        @DisplayName("존재하지 않는 유저로 요청할 경우 404 Not Found 응답을 확인할 수 있다.")
        @Test
        fun returnNotFound_whenInvalidUserIdIsProvided() {
            // when
            val invalidUserId = 999L
            val requestUrl = ENDPOINT_CHARGE()
            val responseType = object : ParameterizedTypeReference<ApiResponse<PointV1Response.Charge>>() {}

            val correctAmount = 1000
            val request = Instancio.of(PointV1Request.Charge::class.java)
                .create()
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("X-USER-ID", invalidUserId.toString())
            }
            val httpEntity = HttpEntity(request, headers)
            val response = testRestTemplate.exchange(
                requestUrl,
                HttpMethod.POST,
                httpEntity,
                responseType,
            )

            // then
            assertAll(
                { assert(response.statusCode.is4xxClientError) },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
            )
        }
    }
}
