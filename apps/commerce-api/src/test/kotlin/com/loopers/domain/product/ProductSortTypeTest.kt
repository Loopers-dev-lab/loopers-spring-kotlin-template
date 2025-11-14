package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

class ProductSortTypeTest {

    @Nested
    @DisplayName("from 생성 테스트")
    inner class From {
        @Test
        @DisplayName("유효한 value가 주어지면 올바른 enum을 반환한다")
        fun `returns correct enum when valid value is given`() {
            // given & when & then
            assertEquals(ProductSortType.LATEST, ProductSortType.from("latest"))
            assertEquals(ProductSortType.PRICE_ASC, ProductSortType.from("price_asc"))
            assertEquals(ProductSortType.LIKES_DESC, ProductSortType.from("likes_desc"))
        }

        @ParameterizedTest
        @CsvSource(
            "LATEST, LATEST",
            "Latest, LATEST",
            "LaTeSt, LATEST",
            "PRICE_ASC, PRICE_ASC",
            "LIKES_DESC, LIKES_DESC",
        )
        @DisplayName("대소문자 구분 없이 생성하면 파싱이 가능하다")
        fun `parses value case-insensitively`(input: String, expected: ProductSortType) {
            // when
            val result = ProductSortType.from(input)

            // then
            assertEquals(expected, result)
        }

        @ParameterizedTest
        @ValueSource(strings = ["invalid", "price_desc", "likes_asc", ""])
        @DisplayName("유효하지 않은 value가 주어지면 CoreException을 던진다")
        fun `throws CoreException when invalid value is given`(invalidValue: String) {
            // when
            val exception = assertThrows<CoreException> {
                ProductSortType.from(invalidValue)
            }

            // then
            assertEquals(ErrorType.BAD_REQUEST, exception.errorType)
            assertEquals("유효하지 않은 정렬 타입입니다.", exception.message)
        }
    }
}
