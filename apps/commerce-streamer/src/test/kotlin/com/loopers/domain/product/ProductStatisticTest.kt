package com.loopers.domain.product

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ProductStatistic 단위 테스트")
class ProductStatisticTest {

    @DisplayName("생성자 테스트")
    @Nested
    inner class Constructor {

        @DisplayName("모든 필드가 올바르게 초기화된다")
        @Test
        fun `all fields are correctly initialized`() {
            // given
            val productId = 1L
            val likeCount = 100L
            val salesCount = 50L
            val viewCount = 1000L

            // when
            val productStatistic = ProductStatistic(
                productId = productId,
                likeCount = likeCount,
                salesCount = salesCount,
                viewCount = viewCount,
            )

            // then
            assertThat(productStatistic.productId).isEqualTo(productId)
            assertThat(productStatistic.likeCount).isEqualTo(likeCount)
            assertThat(productStatistic.salesCount).isEqualTo(salesCount)
            assertThat(productStatistic.viewCount).isEqualTo(viewCount)
        }

        @DisplayName("productId만 지정하면 나머지 카운트들은 0으로 초기화된다")
        @Test
        fun `counts default to zero when only productId is specified`() {
            // given
            val productId = 1L

            // when
            val productStatistic = ProductStatistic(productId = productId)

            // then
            assertThat(productStatistic.productId).isEqualTo(productId)
            assertThat(productStatistic.likeCount).isEqualTo(0L)
            assertThat(productStatistic.salesCount).isEqualTo(0L)
            assertThat(productStatistic.viewCount).isEqualTo(0L)
        }
    }

    @DisplayName("기본값 테스트")
    @Nested
    inner class DefaultValues {

        @DisplayName("likeCount, salesCount, viewCount가 0으로 초기화된다")
        @Test
        fun `likeCount salesCount viewCount are initialized to zero`() {
            // given
            val productId = 42L

            // when
            val productStatistic = ProductStatistic(productId = productId)

            // then
            assertThat(productStatistic.likeCount).isEqualTo(0L)
            assertThat(productStatistic.salesCount).isEqualTo(0L)
            assertThat(productStatistic.viewCount).isEqualTo(0L)
        }
    }

    @DisplayName("엔티티 필드 매핑")
    @Nested
    inner class FieldMapping {

        @DisplayName("영속화 전 id는 0이다")
        @Test
        fun `id is 0 before persistence`() {
            // given
            val productId = 1L

            // when
            val productStatistic = ProductStatistic(productId = productId)

            // then
            assertThat(productStatistic.id).isEqualTo(0L)
        }

        @DisplayName("모든 필드가 올바르게 매핑된다")
        @Test
        fun `all fields are correctly mapped`() {
            // given
            val productId = 999L
            val likeCount = 10L
            val salesCount = 20L
            val viewCount = 30L

            // when
            val productStatistic = ProductStatistic(
                productId = productId,
                likeCount = likeCount,
                salesCount = salesCount,
                viewCount = viewCount,
            )

            // then
            assertThat(productStatistic.id).isEqualTo(0L)
            assertThat(productStatistic.productId).isEqualTo(productId)
            assertThat(productStatistic.likeCount).isEqualTo(likeCount)
            assertThat(productStatistic.salesCount).isEqualTo(salesCount)
            assertThat(productStatistic.viewCount).isEqualTo(viewCount)
        }
    }
}
