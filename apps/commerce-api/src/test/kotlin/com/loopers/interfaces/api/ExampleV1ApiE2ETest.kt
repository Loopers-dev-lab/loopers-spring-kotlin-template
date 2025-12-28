package com.loopers.interfaces.api

import com.loopers.domain.example.ExampleModel
import com.loopers.infrastructure.example.ExampleJpaRepository
import com.loopers.interfaces.api.example.ExampleV1Dto
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
class ExampleV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val exampleJpaRepository: ExampleJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private val ENDPOINT_GET: (Long) -> String = { id: Long -> "/api/v1/examples/$id" }
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api/v1/examples/{id}")
    @Nested
    inner class Get {
        @DisplayName("존재하는 예시 ID를 주면, 해당 예시 정보를 반환한다.")
        @Test
        fun returnsExampleInfo_whenValidIdIsProvided() {
            // arrange
            val exampleModel = exampleJpaRepository.save(ExampleModel(name = "예시 제목", description = "예시 설명"))
            val requestUrl = ENDPOINT_GET(exampleModel.id)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<ExampleV1Dto.ExampleResponse>>() {}
            val response = testRestTemplate.exchange(requestUrl, HttpMethod.GET, HttpEntity<Any>(Unit), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.id).isEqualTo(exampleModel.id) },
                { assertThat(response.body?.data?.name).isEqualTo(exampleModel.name) },
                { assertThat(response.body?.data?.description).isEqualTo(exampleModel.description) },
            )
        }

        @DisplayName("숫자가 아닌 ID 로 요청하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun throwsBadRequest_whenIdIsNotProvided() {
            // arrange
            val requestUrl = "/api/v1/examples/나나"

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<ExampleV1Dto.ExampleResponse>>() {}
            val response = testRestTemplate.exchange(requestUrl, HttpMethod.GET, HttpEntity<Any>(Unit), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode.is4xxClientError).isTrue },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
            )
        }

        @DisplayName("존재하지 않는 예시 ID를 주면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun throwsException_whenInvalidIdIsProvided() {
            // arrange
            val invalidId = -1L
            val requestUrl = ENDPOINT_GET(invalidId)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<ExampleV1Dto.ExampleResponse>>() {}
            val response = testRestTemplate.exchange(requestUrl, HttpMethod.GET, HttpEntity<Any>(Unit), responseType)

            // assert
            assertAll(
                { assert(response.statusCode.is4xxClientError) },
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
            )
        }
    }
}
