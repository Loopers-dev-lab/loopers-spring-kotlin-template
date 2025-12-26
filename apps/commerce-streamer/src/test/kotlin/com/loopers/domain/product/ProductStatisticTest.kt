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

    @DisplayName("applyLikeChanges 도메인 메서드 테스트")
    @Nested
    inner class ApplyLikeChanges {

        @DisplayName("CREATED 타입만 있으면 likeCount가 증가한다")
        @Test
        fun `likeCount increases with CREATED types only`() {
            // given
            val productStatistic = ProductStatistic(productId = 1L, likeCount = 10L)
            val types = listOf(
                UpdateLikeCountCommand.LikeType.CREATED,
                UpdateLikeCountCommand.LikeType.CREATED,
                UpdateLikeCountCommand.LikeType.CREATED,
            )

            // when
            productStatistic.applyLikeChanges(types)

            // then
            assertThat(productStatistic.likeCount).isEqualTo(13L)
        }

        @DisplayName("CANCELED 타입만 있으면 likeCount가 감소한다")
        @Test
        fun `likeCount decreases with CANCELED types only`() {
            // given
            val productStatistic = ProductStatistic(productId = 1L, likeCount = 10L)
            val types = listOf(
                UpdateLikeCountCommand.LikeType.CANCELED,
                UpdateLikeCountCommand.LikeType.CANCELED,
            )

            // when
            productStatistic.applyLikeChanges(types)

            // then
            assertThat(productStatistic.likeCount).isEqualTo(8L)
        }

        @DisplayName("CREATED와 CANCELED가 섞여 있으면 delta가 계산되어 적용된다")
        @Test
        fun `likeCount applies delta with mixed CREATED and CANCELED types`() {
            // given
            val productStatistic = ProductStatistic(productId = 1L, likeCount = 10L)
            val types = listOf(
                UpdateLikeCountCommand.LikeType.CREATED,
                UpdateLikeCountCommand.LikeType.CREATED,
                UpdateLikeCountCommand.LikeType.CREATED,
                UpdateLikeCountCommand.LikeType.CANCELED,
            )

            // when
            productStatistic.applyLikeChanges(types)

            // then
            assertThat(productStatistic.likeCount).isEqualTo(12L) // 10 + 3 - 1 = 12
        }

        @DisplayName("likeCount가 0 미만으로 내려가지 않는다 (maxOf 보호)")
        @Test
        fun `likeCount does not go below zero with maxOf protection`() {
            // given
            val productStatistic = ProductStatistic(productId = 1L, likeCount = 2L)
            val types = listOf(
                UpdateLikeCountCommand.LikeType.CANCELED,
                UpdateLikeCountCommand.LikeType.CANCELED,
                UpdateLikeCountCommand.LikeType.CANCELED,
                UpdateLikeCountCommand.LikeType.CANCELED,
                UpdateLikeCountCommand.LikeType.CANCELED,
            )

            // when
            productStatistic.applyLikeChanges(types)

            // then
            assertThat(productStatistic.likeCount).isEqualTo(0L)
        }

        @DisplayName("빈 리스트가 전달되면 likeCount가 변경되지 않는다")
        @Test
        fun `likeCount remains unchanged with empty list`() {
            // given
            val productStatistic = ProductStatistic(productId = 1L, likeCount = 10L)
            val types = emptyList<UpdateLikeCountCommand.LikeType>()

            // when
            productStatistic.applyLikeChanges(types)

            // then
            assertThat(productStatistic.likeCount).isEqualTo(10L)
        }
    }

    @DisplayName("applySalesChanges 도메인 메서드 테스트")
    @Nested
    inner class ApplySalesChanges {

        @DisplayName("여러 수량이 합산되어 salesCount에 더해진다")
        @Test
        fun `salesCount increases by sum of all quantities`() {
            // given
            val productStatistic = ProductStatistic(productId = 1L, salesCount = 100L)
            val quantities = listOf(5, 3, 2)

            // when
            productStatistic.applySalesChanges(quantities)

            // then
            assertThat(productStatistic.salesCount).isEqualTo(110L) // 100 + 5 + 3 + 2 = 110
        }

        @DisplayName("단일 수량이 salesCount에 더해진다")
        @Test
        fun `salesCount increases by single quantity`() {
            // given
            val productStatistic = ProductStatistic(productId = 1L, salesCount = 50L)
            val quantities = listOf(10)

            // when
            productStatistic.applySalesChanges(quantities)

            // then
            assertThat(productStatistic.salesCount).isEqualTo(60L)
        }

        @DisplayName("빈 리스트가 전달되면 salesCount가 변경되지 않는다")
        @Test
        fun `salesCount remains unchanged with empty list`() {
            // given
            val productStatistic = ProductStatistic(productId = 1L, salesCount = 50L)
            val quantities = emptyList<Int>()

            // when
            productStatistic.applySalesChanges(quantities)

            // then
            assertThat(productStatistic.salesCount).isEqualTo(50L)
        }
    }

    @DisplayName("applyViewChanges 도메인 메서드 테스트")
    @Nested
    inner class ApplyViewChanges {

        @DisplayName("count만큼 viewCount가 증가한다")
        @Test
        fun `viewCount increases by count`() {
            // given
            val productStatistic = ProductStatistic(productId = 1L, viewCount = 1000L)
            val count = 5

            // when
            productStatistic.applyViewChanges(count)

            // then
            assertThat(productStatistic.viewCount).isEqualTo(1005L)
        }

        @DisplayName("count가 0이면 viewCount가 변경되지 않는다")
        @Test
        fun `viewCount remains unchanged when count is zero`() {
            // given
            val productStatistic = ProductStatistic(productId = 1L, viewCount = 1000L)
            val count = 0

            // when
            productStatistic.applyViewChanges(count)

            // then
            assertThat(productStatistic.viewCount).isEqualTo(1000L)
        }

        @DisplayName("count가 1이면 viewCount가 1 증가한다")
        @Test
        fun `viewCount increases by one when count is one`() {
            // given
            val productStatistic = ProductStatistic(productId = 1L, viewCount = 500L)
            val count = 1

            // when
            productStatistic.applyViewChanges(count)

            // then
            assertThat(productStatistic.viewCount).isEqualTo(501L)
        }
    }
}
