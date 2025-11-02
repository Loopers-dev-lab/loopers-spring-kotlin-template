package com.loopers.interfaces.api.user

import com.loopers.E2ETestSupport
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
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
import org.springframework.http.HttpMethod

class UserApiE2ETest(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val userRepository: UserRepository,
) : E2ETestSupport() {

    companion object {
        private const val ENDPOINT_POST = "/api/v1/users"
        private const val ENDPOINT_GET = "/api/v1/users/{loginId}"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    inner class Post {
        @DisplayName("회원 가입이 성공할 경우, 생성된 유저 정보를 응답으로 반환한다.")
        @Test
        fun returnsUserInfo_whenValidUserRequestIsProvided() {
            // arrange
            val requestUrl = ENDPOINT_POST
            val requestBody = UserRegisterDto.Request(
                loginId = "sonjs7554",
                birthDate = "1994-03-12",
                gender = "MALE",
                email = "sonjs7554@naver.com",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserRegisterDto.Response>>() {}
            val response = testRestTemplate.exchange(requestUrl, HttpMethod.POST, HttpEntity<Any>(requestBody), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.loginId).isEqualTo(requestBody.loginId) },
            )
        }

        @DisplayName("회원 가입 시에 성별이 없을 경우, 400 BAD REQUEST 응답을 반환한다")
        @Test
        fun return400BadRequest_whenGenderIsMissing() {
            // arrange
            val requestUrl = ENDPOINT_POST

            val missingGenderRequestBody = mapOf(
                "loginId" to "sonjs7554",
                "email" to "test@example.com",
                "birthDate" to "1994-03-12",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserRegisterDto.Response>>() {}
            val response = testRestTemplate.exchange(requestUrl, HttpMethod.POST, HttpEntity<Any>(missingGenderRequestBody), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode.is4xxClientError).isTrue() },
                { assertThat(response.body?.meta?.message).isEqualTo("필수 필드 'gender'이(가) 누락되었습니다.") },
            )
        }

        // TODO : 빈 값인 경우에 대한 Valid 에 대한 테스트 코드 구현
    }

    @DisplayName("GET /api/vi/users/{loginId}")
    @Nested
    inner class GetUserInfoByLoginId {
        @DisplayName("내 정보 조회에 성공할 경우, 해당하는 유저 정보를 응답으로 반환한다.")
        @Test
        fun returnUserInfo_whenLoginIdExists() {
            // arrange
            val userModel = UserFixture.create()
            val user = userRepository.save(userModel)
            val requestUrl = ENDPOINT_GET.replace("{loginId}", user.loginId.value)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserInfoDto.Response>>() {}
            val response = testRestTemplate.exchange(requestUrl, HttpMethod.GET, HttpEntity<Any>(Unit), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.loginId).isEqualTo(user.loginId.value) },
                { assertThat(response.body?.data?.email).isEqualTo(user.email.value) },
                { assertThat(response.body?.data?.birthDate).isEqualTo(user.birthDate.value) },
                { assertThat(response.body?.data?.gender).isEqualTo(user.gender.name) },
            )
        }

        @DisplayName("존재하지 않는 ID로 조회할 경우, 404 Not Found 응답을 반환한다.")
        @Test
        fun return404NotFound_whenLoginIdDoesNotExist() {
            // arrange
            val notExistLoginId = "notExist12"
            val requestUrl = ENDPOINT_GET.replace("{loginId}", notExistLoginId)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserInfoDto.Response>>() {}
            val response = testRestTemplate.exchange(requestUrl, HttpMethod.GET, HttpEntity<Any>(Unit), responseType)

            print(response)
            // assert
            assertAll(
                { assertThat(response.statusCode.is4xxClientError).isTrue },
            )
        }
    }
}
