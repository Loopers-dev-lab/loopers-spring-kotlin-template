package com.loopers.domain.product

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ProductSortTest {

    @Nested
    @DisplayName("from 메서드는")
    inner class From {

        @Test
        fun `유효한 문자열을 올바른 enum으로 변환한다`() {
            assertThat(ProductSort.from("latest")).isEqualTo(ProductSort.LATEST)
            assertThat(ProductSort.from("price_asc")).isEqualTo(ProductSort.PRICE_ASC)
            assertThat(ProductSort.from("likes_desc")).isEqualTo(ProductSort.LIKE_DESC)
        }

        @Test
        fun `null 입력 시 기본값 LATEST를 반환한다`() {
            assertThat(ProductSort.from(null)).isEqualTo(ProductSort.LATEST)
        }

        @Test
        fun `잘못된 값 입력 시 IllegalArgumentException을 던진다`() {
            assertThatThrownBy { ProductSort.from("invalid_value") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid ProductSort value")
        }
    }
}
