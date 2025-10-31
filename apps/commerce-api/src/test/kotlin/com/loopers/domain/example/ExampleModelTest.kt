package com.loopers.domain.example

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

@Disabled("테스트 비활성화")
class ExampleModelTest {
    @DisplayName("예시 모델을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("제목과 설명이 모두 주어지면, 정상적으로 생성된다.")
        @Test
        fun createsExampleModel_whenNameAndDescriptionAreProvided() {
            // arrange
            val name = "제목"
            val description = "설명"

            // act
            val exampleModel = ExampleModel(name = name, description = description)

            // assert
            assertAll(
                { assertThat(exampleModel.id).isNotNull() },
                { assertThat(exampleModel.name).isEqualTo(name) },
                { assertThat(exampleModel.description).isEqualTo(description) },
            )
        }

        @DisplayName("제목이 빈칸으로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenTitleIsBlank() {
            // arrange
            val name = "   "

            // act
            val result = assertThrows<CoreException> {
                ExampleModel(name = name, description = "설명")
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("설명이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenDescriptionIsEmpty() {
            // arrange
            val description = ""

            // act
            val result = assertThrows<CoreException> {
                ExampleModel(name = "제목", description = description)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
