package com.loopers.application.ranking

import com.loopers.domain.ranking.ProductRanking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("CachedRankingV1")
class CachedRankingV1Test {

    @Nested
    @DisplayName("from")
    inner class FromTest {

        @Test
        @DisplayName("ProductRanking 리스트를 CachedRankingV1로 변환할 수 있다")
        fun `should convert ProductRanking list to CachedRankingV1`() {
            // given
            val rankings = listOf(
                ProductRanking(productId = 1L, rank = 1, score = BigDecimal("100.5")),
                ProductRanking(productId = 2L, rank = 2, score = BigDecimal("90.0")),
                ProductRanking(productId = 3L, rank = 3, score = BigDecimal("80.25")),
            )

            // when
            val cached = CachedRankingV1.from(rankings)

            // then
            assertThat(cached.rankings).hasSize(3)
            assertThat(cached.rankings[0].productId).isEqualTo(1L)
            assertThat(cached.rankings[0].rank).isEqualTo(1)
            assertThat(cached.rankings[0].score).isEqualByComparingTo(BigDecimal("100.5"))

            assertThat(cached.rankings[1].productId).isEqualTo(2L)
            assertThat(cached.rankings[1].rank).isEqualTo(2)
            assertThat(cached.rankings[1].score).isEqualByComparingTo(BigDecimal("90.0"))

            assertThat(cached.rankings[2].productId).isEqualTo(3L)
            assertThat(cached.rankings[2].rank).isEqualTo(3)
            assertThat(cached.rankings[2].score).isEqualByComparingTo(BigDecimal("80.25"))
        }

        @Test
        @DisplayName("빈 리스트를 변환하면 빈 CachedRankingV1이 생성된다")
        fun `should convert empty list to empty CachedRankingV1`() {
            // given
            val rankings = emptyList<ProductRanking>()

            // when
            val cached = CachedRankingV1.from(rankings)

            // then
            assertThat(cached.rankings).isEmpty()
        }
    }

    @Nested
    @DisplayName("toProductRankings")
    inner class ToProductRankingsTest {

        @Test
        @DisplayName("CachedRankingV1을 ProductRanking 리스트로 변환할 수 있다")
        fun `should convert CachedRankingV1 to ProductRanking list`() {
            // given
            val cached = CachedRankingV1(
                rankings = listOf(
                    CachedRankingV1.Entry(productId = 1L, rank = 1, score = BigDecimal("100.5")),
                    CachedRankingV1.Entry(productId = 2L, rank = 2, score = BigDecimal("90.0")),
                ),
            )

            // when
            val rankings = cached.toProductRankings()

            // then
            assertThat(rankings).hasSize(2)
            assertThat(rankings[0]).isEqualTo(
                ProductRanking(productId = 1L, rank = 1, score = BigDecimal("100.5")),
            )
            assertThat(rankings[1]).isEqualTo(
                ProductRanking(productId = 2L, rank = 2, score = BigDecimal("90.0")),
            )
        }

        @Test
        @DisplayName("빈 CachedRankingV1을 변환하면 빈 리스트가 반환된다")
        fun `should convert empty CachedRankingV1 to empty list`() {
            // given
            val cached = CachedRankingV1(rankings = emptyList())

            // when
            val rankings = cached.toProductRankings()

            // then
            assertThat(rankings).isEmpty()
        }
    }

    @Nested
    @DisplayName("round-trip conversion")
    inner class RoundTripTest {

        @Test
        @DisplayName("ProductRanking -> CachedRankingV1 -> ProductRanking 변환이 동일한 데이터를 유지해야 한다")
        fun `should maintain data after round-trip conversion`() {
            // given
            val originalRankings = listOf(
                ProductRanking(productId = 1L, rank = 1, score = BigDecimal("100.5")),
                ProductRanking(productId = 2L, rank = 2, score = BigDecimal("90.0")),
                ProductRanking(productId = 3L, rank = 3, score = BigDecimal("80.25")),
            )

            // when
            val cached = CachedRankingV1.from(originalRankings)
            val restoredRankings = cached.toProductRankings()

            // then
            assertThat(restoredRankings).hasSize(originalRankings.size)
            restoredRankings.forEachIndexed { index, restored ->
                val original = originalRankings[index]
                assertThat(restored.productId).isEqualTo(original.productId)
                assertThat(restored.rank).isEqualTo(original.rank)
                assertThat(restored.score).isEqualByComparingTo(original.score)
            }
        }
    }
}
