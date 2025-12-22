package com.loopers.domain.product

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ProductStatisticTest {
    @DisplayName("ProductStatistic 생성 테스트")
    @Nested
    inner class Create {

        @DisplayName("create 팩토리 메서드로 ProductStatistic을 생성하면 likeCount, salesCount, viewCount가 0으로 초기화된다")
        @Test
        fun `create factory method initializes all counts to zero`() {
            // given
            val productId = 1L

            // when
            val productStatistic = ProductStatistic.create(productId)

            // then
            assertThat(productStatistic.productId).isEqualTo(productId)
            assertThat(productStatistic.likeCount).isEqualTo(0L)
            assertThat(productStatistic.salesCount).isEqualTo(0L)
            assertThat(productStatistic.viewCount).isEqualTo(0L)
        }
    }

    @DisplayName("ProductStatistic of 팩토리 메서드 테스트")
    @Nested
    inner class Of {

        @DisplayName("of 팩토리 메서드로 모든 필드를 지정하여 ProductStatistic을 생성할 수 있다")
        @Test
        fun `of factory method creates ProductStatistic with all fields`() {
            // given
            val productId = 1L
            val likeCount = 100L
            val salesCount = 50L
            val viewCount = 1000L

            // when
            val productStatistic = ProductStatistic.of(productId, likeCount, salesCount, viewCount)

            // then
            assertThat(productStatistic.productId).isEqualTo(productId)
            assertThat(productStatistic.likeCount).isEqualTo(likeCount)
            assertThat(productStatistic.salesCount).isEqualTo(salesCount)
            assertThat(productStatistic.viewCount).isEqualTo(viewCount)
        }

        @DisplayName("of 팩토리 메서드에서 salesCount와 viewCount를 생략하면 0으로 초기화된다")
        @Test
        fun `of factory method uses default values for salesCount and viewCount`() {
            // given
            val productId = 1L
            val likeCount = 100L

            // when
            val productStatistic = ProductStatistic.of(productId, likeCount)

            // then
            assertThat(productStatistic.productId).isEqualTo(productId)
            assertThat(productStatistic.likeCount).isEqualTo(likeCount)
            assertThat(productStatistic.salesCount).isEqualTo(0L)
            assertThat(productStatistic.viewCount).isEqualTo(0L)
        }
    }
}
