package com.loopers.interfaces.api

import com.loopers.domain.member.BirthDate
import com.loopers.domain.member.Gender
import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberId
import com.loopers.domain.shared.Email
import com.loopers.infrastructure.member.MemberJpaRepository
import com.loopers.interfaces.api.point.PointV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.TestPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = ["spring.task.scheduling.enabled=false"])
class Point1V1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val memberJpaRepository: MemberJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp
) {

    @MockBean
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api/v1/points")
    @Nested
    inner class GetPoint {
        @DisplayName("포인트 조회에 성공할 경우, 보유 포인트를 응답으로 반환한다.")
        @Test
        fun successfulGetPoint() {
            val memberId = MemberId("testUser1")
            val member = Member(memberId, Email("test@gmail.com"), BirthDate.from("1990-05-15"), Gender.MALE)
            member.chargePoint(1000L)
            memberJpaRepository.save(member)

            val headers = HttpHeaders().apply { set("X-USER-ID", memberId.value) }

            val responseType = object : ParameterizedTypeReference<ApiResponse<PointV1Dto.PointResponse>>() {}
            val response = testRestTemplate.exchange("/api/v1/points", HttpMethod.GET, HttpEntity<Any>(headers), responseType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.point).isEqualTo(1000L)
        }

        @DisplayName("X-USER-ID 헤더가 없을 경우, 400 Bad Request 응답을 반환한다.")
        @Test
        fun failWithoutXUserIdHeader() {
            val response = testRestTemplate.exchange("/api/v1/points", HttpMethod.GET, null, String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("POST /api/v1/points/charge")
    @Nested
    inner class ChargePoint {
        @DisplayName(" 존재하는 유저가 1000원을 충전할 경우, 충전된 보유 총량을 응답으로 반환한다.")
        @Test
        fun successfulChargePoint() {
            val memberId = MemberId("testUser1")
            val member = Member(memberId, Email("test@gmail.com"), BirthDate.from("1990-05-15"), Gender.MALE)
            memberJpaRepository.save(member)
            val request = PointV1Dto.ChargePointRequest(1000L)
            val headers = HttpHeaders().apply { set("X-USER-ID", memberId.value) }


            val responseType = object : ParameterizedTypeReference<ApiResponse<PointV1Dto.PointResponse>>() {}
            val response = testRestTemplate.exchange("/api/v1/points/charge", HttpMethod.POST, HttpEntity(request, headers), responseType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.point).isEqualTo(1000L)
        }

        @DisplayName("존재하지 않는 유저로 요청할 경우, 404 Not Found 응답을 반환한다")
        @Test
        fun failChargePointWithNonexistentMemberId() {
            val request = PointV1Dto.ChargePointRequest(1000L)
            val headers = HttpHeaders().apply { set("X-USER-ID", "noUser1") }

            val responseType = object : ParameterizedTypeReference<ApiResponse<PointV1Dto.PointResponse>>() {}
            val response = testRestTemplate.exchange("/api/v1/points/charge", HttpMethod.POST, HttpEntity(request, headers), responseType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }
}
