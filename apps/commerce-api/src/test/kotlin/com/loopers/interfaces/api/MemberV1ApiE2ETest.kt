package com.loopers.interfaces.api

import com.loopers.domain.member.BirthDate
import com.loopers.domain.member.Gender
import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberId
import com.loopers.domain.shared.Email
import com.loopers.infrastructure.member.MemberJpaRepository
import com.loopers.interfaces.api.member.JoinMemberRequest
import com.loopers.interfaces.api.member.MemberV1Dto
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
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val memberJpaRepository: MemberJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/users/join - 회원 가입")
    @Nested
    inner class Join {
        @DisplayName("회원 가입이 성공할 경우, 생성된 유저 정보를 응답으로 반환한다")
        @Test
        fun successfulJoin() {
            val request = JoinMemberRequest("testUser1", "test@gmail.com", "1990-05-15", "MALE")

            val responseType = object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            val response = testRestTemplate.exchange("/api/v1/users/join", HttpMethod.POST, HttpEntity(request), responseType)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.memberId).isEqualTo("testUser1") },
                { assertThat(response.body?.data?.email).isEqualTo("test@gmail.com") },
                { assertThat(response.body?.data?.gender).isEqualTo("MALE") },
            )
        }

        @DisplayName("회원 가입 시에 성별이 없을 경우, 400 Bad Request 응답을 반환한다")
        @Test
        fun failWithoutGender() {
            val request = mapOf(
                "memberId" to "testUser1",
                "email" to "test@gmail.com",
                "birthDate" to "1990-05-15"
                // gender 없음
            )
            val response = testRestTemplate.postForEntity("/api/v1/users/join", request, String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }


    @DisplayName("POST /api/v1/users/{memberId} - 회원 정보 조회")
    @Nested
    inner class GetMemberByMemberId {
        @DisplayName("내 정보 조회에 성공할 경우, 해당하는 유저 정보를 응답으로 반환한다")
        @Test
        fun successfulGetByMemberId() {
            val member = Member(MemberId("testUser1"), Email("test@gmail.com"), BirthDate.from("1990-05-15"), Gender.MALE)
            memberJpaRepository.save(member)

            val responseType = object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            val response = testRestTemplate.exchange("/api/v1/users/testUser1", HttpMethod.GET, null, responseType)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.memberId).isEqualTo("testUser1") },
                { assertThat(response.body?.data?.email).isEqualTo("test@gmail.com") },
                { assertThat(response.body?.data?.gender).isEqualTo("MALE") },
            )
        }

        @DisplayName("존재하지 않는 ID로 조회할 경우, 404 Not Found 응답을 반환한다")
        @Test
        fun failWithNonexistentMemberId() {
            val responseType = object : ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            val response = testRestTemplate.exchange("/api/v1/users/noUser1", HttpMethod.GET, null, responseType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }
}
