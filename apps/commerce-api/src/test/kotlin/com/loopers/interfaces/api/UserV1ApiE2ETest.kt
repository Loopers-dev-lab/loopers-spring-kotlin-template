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
        private const val ENDPOINT_REGISTER = "/api/v1/users/register"
        private val ENDPOINT_GETUSER: (String) -> String = { userId: String -> "/api/v1/users/$userId" }
        private const val ENDPOINT_GETPOINT = "/api/v1/users/point"
        private const val ENDPOINT_CHARGEPOINT = "/api/v1/users/chargePoint"
    }

    @AfterEach
    fun cleanUp() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/users/register")
    @Nested
    inner class RegisterUser {
        @DisplayName("회원 가입이 성공할 경우, 생성된 유저 정보를 응답으로 반환한다.")
        @Test
        fun returnsUserInfo_whenRegisterUser() {
            // arrange
            val req = UserV1Dto.RegisterUserRequest(
                userId = "testId",
                email = "test@test.com",
                birth = "2025-10-25",
                gender = Gender.OTHER,
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            val res = testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, HttpEntity(req), responseType)

            // assert
            assertAll(
                { assertThat(res.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(res.body?.data?.userId).isEqualTo(req.userId) },
                { assertThat(res.body?.data?.email).isEqualTo(req.email) },
                { assertThat(res.body?.data?.birth).isEqualTo(req.birth) },
                { assertThat(res.body?.data?.gender).isEqualTo(req.gender) },
            )
        }

        @DisplayName("회원 가입 시에 성별이 없을 경우, 400 Bad Request 응답을 반환한다.")
        @Test
        fun throwsBadRequest_whenGenderIsNotProvided() {
            // arrange
            val req = UserV1Dto.RegisterUserRequest(
                userId = "testId",
                email = "test@test.com",
                birth = "2025-10-25",
                gender = Gender.NONE,
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            val res = testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, HttpEntity(req), responseType)

            // assert
            assertAll(
                { assertThat(res.statusCode.is4xxClientError).isTrue },
                { assertThat(res.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
            )
        }
    }

    @DisplayName("POST /api/v1/users/{userId}")
    @Nested
    inner class GetUser {
        @DisplayName("존재하는 회원의 ID를 주면, 생성된 유저 정보를 응답으로 반환한다.")
        @Test
        fun returnsUserInfo_whenUserIdExists() {
            // arrange
            val user = userJpaRepository.save(User(userId = "testId", email = "test@test.com", birth = "2025-10-25", gender = Gender.OTHER))

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            val res = testRestTemplate.exchange(ENDPOINT_GETUSER(user.userId), HttpMethod.GET, HttpEntity<Any>(Unit), responseType)

            // assert
            assertAll(
                { assertThat(res.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(res.body?.data?.userId).isEqualTo(user.userId) },
                { assertThat(res.body?.data?.email).isEqualTo(user.email) },
                { assertThat(res.body?.data?.birth).isEqualTo(user.birth) },
                { assertThat(res.body?.data?.gender).isEqualTo(user.gender) },
            )
        }

        @DisplayName("존재하지 않는 ID로 조회할 경우, 404 Not Found 응답을 반환한다.")
        @Test
        fun throwsNotFound_whenUserIdNotExists() {
            // arrange
            val userId = "testId"

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            val res = testRestTemplate.exchange(ENDPOINT_GETUSER(userId), HttpMethod.GET, HttpEntity<Any>(Unit), responseType)

            // assert
            assertAll(
                { assertThat(res.statusCode.is4xxClientError).isTrue },
                { assertThat(res.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
            )
        }
    }

    @DisplayName("POST /api/v1/point/{userId}")
    @Nested
    inner class GetPoint {
        @DisplayName("존재하는 회원의 ID를 주면, 보유 포인트를 응답으로 반환한다.")
        @Test
        fun returnsUserInfo_whenUserIdExists() {
            // arrange
            val user = userJpaRepository.save(User(userId = "testId", email = "test@test.com", birth = "2025-10-25", gender = Gender.OTHER))
            val headers = HttpHeaders()
            headers.set("X-USER-ID", user.userId)
            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Int>>() {}
            val res = testRestTemplate.exchange(ENDPOINT_GETPOINT, HttpMethod.GET, HttpEntity<Any>(Unit, headers), responseType)

            // assert
            assertAll(
                { assertThat(res.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(res.body?.data).isNotNull() },
                { assertThat(res.body?.data).isEqualTo(0) },
            )
        }

        @DisplayName("X-USER_ID 헤더가 없을 경우, 400 Bad Request 응답을 반환한다.")
        @Test
        fun throwsBadRequest_whenUserIdHeaderNotExists() {
            // arrange

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Int>>() {}
            val res = testRestTemplate.exchange(ENDPOINT_GETPOINT, HttpMethod.GET, HttpEntity<Any>(Unit), responseType)

            // assert
            assertAll(
                { assertThat(res.statusCode.is4xxClientError).isTrue },
                { assertThat(res.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
            )
        }
    }

    @DisplayName("POST /api/v1/users/chargePoint")
    @Nested
    inner class ChargePoint {
        @DisplayName("존재하는 유저가 1000원을 충전할 경우, 충전된 보유 총량을 응답으로 반환한다.")
        @Test
        fun returnsUserPoint_whenUserExists() {
            // arrange
            val user = userJpaRepository.save(User(userId = "testId", email = "test@test.com", birth = "2025-10-25", gender = Gender.OTHER))
            val req = UserV1Dto.ChargePointRequest(
                userId = user.userId,
                amount = 1000,
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Int>>() {}
            val res = testRestTemplate.exchange(ENDPOINT_CHARGEPOINT, HttpMethod.POST, HttpEntity(req), responseType)

            // assert
            assertAll(
                { assertThat(res.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(res.body?.data).isEqualTo(1000) },
            )
        }

        @DisplayName("존재하지 않는 유저로 요청할 경우, 404 Not Found 응답을 반환한다.")
        @Test
        fun throwNotFound_whenUserNotExists() {
            // arrange
            val req = UserV1Dto.ChargePointRequest(
                userId = "testId",
                amount = 1000,
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Int>>() {}
            val res = testRestTemplate.exchange(ENDPOINT_CHARGEPOINT, HttpMethod.POST, HttpEntity(req), responseType)

            // assert
            assertAll(
                { assertThat(res.statusCode.is4xxClientError).isTrue },
                { assertThat(res.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
            )
        }
    }
}
