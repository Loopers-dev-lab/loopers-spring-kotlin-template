package com.loopers.domain.example

import com.loopers.infrastructure.example.ExampleJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@Disabled("테스트 비활성화")
@SpringBootTest
class ExampleServiceIntegrationTest @Autowired constructor(
    private val exampleService: ExampleService,
    private val exampleJpaRepository: ExampleJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("예시를 조회할 때,")
    @Nested
    inner class Get {
        @DisplayName("존재하는 예시 ID를 주면, 해당 예시 정보를 반환한다.")
        @Test
        fun returnsExampleInfo_whenValidIdIsProvided() {
            // arrange
            val exampleModel = exampleJpaRepository.save(ExampleModel(name = "예시 제목", description = "예시 설명"))

            // act
            val result = exampleService.getExample(exampleModel.id)

            // assert
            assertAll(
                { assertThat(result).isNotNull() },
                { assertThat(result.id).isEqualTo(exampleModel.id) },
                { assertThat(result.name).isEqualTo(exampleModel.name) },
                { assertThat(result.description).isEqualTo(exampleModel.description) },
            )
        }

        @DisplayName("존재하지 않는 예시 ID를 주면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsException_whenInvalidIdIsProvided() {
            // arrange
            val invalidId = 999L // Assuming this ID does not exist

            // act
            val exception = assertThrows<CoreException> {
                exampleService.getExample(invalidId)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
