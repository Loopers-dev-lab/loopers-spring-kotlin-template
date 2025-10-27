package com.loopers.interfaces.api

import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.user.UserV1Dto
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
class UserV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT_REGISTER_USER = "/api/v1/users"
        private val ENDPOINT_GET_USER: (String) -> String = { userId: String -> "/api/v1/users/$userId" }
        private const val ENDPOINT_GET_POINT = "/api/v1/points"
        private const val ENDPOINT_CHARGE_POINT = "/api/v1/points/charge"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    inner class RegisterUser {
        @DisplayName("회원 가입이 성공할 경우, 생성된 유저 정보를 응답으로 반환한다.")
        @Test
        fun returnsCreatedUser_whenRegistrationIsSuccessful() {
            // arrange
            val request = UserV1Dto.RegisterUserRequest(
                userId = "testuser",
                email = "test@example.com",
                birthDate = "1990-01-01",
                gender = Gender.MALE,
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_REGISTER_USER,
                HttpMethod.POST,
                HttpEntity(request),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.userId).isEqualTo("testuser") },
                { assertThat(response.body?.data?.email).isEqualTo("test@example.com") },
                { assertThat(response.body?.data?.gender).isEqualTo(Gender.MALE) },
                { assertThat(response.body?.data?.point).isEqualTo(0L) },
            )
        }

        @DisplayName("회원 가입 시에 성별이 없을 경우, 400 Bad Request 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whenGenderIsMissing() {
            // arrange
            val requestJson = """
                {
                    "userId": "testuser",
                    "email": "test@example.com",
                    "birthDate": "1990-01-01"
                }
            """.trimIndent()

            val headers = HttpHeaders()
            headers.set("Content-Type", "application/json")

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_REGISTER_USER,
                HttpMethod.POST,
                HttpEntity(requestJson, headers),
                String::class.java,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is4xxClientError).isTrue() },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
            )
        }
    }

    @DisplayName("GET /api/v1/users/{userId}")
    @Nested
    inner class GetUserByUserId {
        @DisplayName("내 정보 조회에 성공할 경우, 해당하는 유저 정보를 응답으로 반환한다.")
        @Test
        fun returnsUserInfo_whenUserExists() {
            // arrange
            val user = userJpaRepository.save(
                User(
                    userId = "testuser",
                    email = "test@example.com",
                    birthDate = "1990-01-01",
                    gender = Gender.MALE,
                ),
            )
            val requestUrl = ENDPOINT_GET_USER("testuser")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            val response = testRestTemplate.exchange(
                requestUrl,
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.id).isEqualTo(user.id) },
                { assertThat(response.body?.data?.userId).isEqualTo("testuser") },
                { assertThat(response.body?.data?.email).isEqualTo("test@example.com") },
            )
        }

        @DisplayName("존재하지 않는 ID로 조회할 경우, 404 Not Found 응답을 반환한다.")
        @Test
        fun returnsNotFound_whenUserDoesNotExist() {
            // arrange
            val requestUrl = ENDPOINT_GET_USER("nonexistent")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            val response = testRestTemplate.exchange(
                requestUrl,
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is4xxClientError).isTrue() },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
            )
        }
    }

    @DisplayName("GET /api/v1/points")
    @Nested
    inner class GetPoint {
        @DisplayName("포인트 조회에 성공할 경우, 보유 포인트를 응답으로 반환한다.")
        @Test
        fun returnsPoint_whenUserExists() {
            // arrange
            userJpaRepository.save(
                User(
                    userId = "testuser",
                    email = "test@example.com",
                    birthDate = "1990-01-01",
                    gender = Gender.MALE,
                ),
            )

            val headers = HttpHeaders()
            headers.set("X-USER-ID", "testuser")

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.PointResponse>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_GET_POINT,
                HttpMethod.GET,
                HttpEntity<Any>(Unit, headers),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.point).isEqualTo(0L) },
            )
        }

        @DisplayName("X-USER-ID 헤더가 없을 경우, 400 Bad Request 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whenUserIdHeaderIsMissing() {
            // arrange & act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.PointResponse>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_GET_POINT,
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
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
        @DisplayName("존재하는 유저가 1000원을 충전할 경우, 충전된 보유 총량을 응답으로 반환한다.")
        @Test
        fun returnsChargedPoint_whenChargingIsSuccessful() {
            // arrange
            userJpaRepository.save(
                User(
                    userId = "testuser",
                    email = "test@example.com",
                    birthDate = "1990-01-01",
                    gender = Gender.MALE,
                ),
            )

            val headers = HttpHeaders()
            headers.set("X-USER-ID", "testuser")
            val request = UserV1Dto.ChargePointRequest(amount = 1000L)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.PointResponse>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_CHARGE_POINT,
                HttpMethod.POST,
                HttpEntity(request, headers),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.point).isEqualTo(1000L) },
            )
        }

        @DisplayName("존재하지 않는 유저로 요청할 경우, 404 Not Found 응답을 반환한다.")
        @Test
        fun returnsNotFound_whenUserDoesNotExist() {
            // arrange
            val headers = HttpHeaders()
            headers.set("X-USER-ID", "nonexistent")
            val request = UserV1Dto.ChargePointRequest(amount = 1000L)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.PointResponse>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_CHARGE_POINT,
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
