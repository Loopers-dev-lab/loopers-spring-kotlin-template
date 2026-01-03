package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

@DisplayName("ProductRankDaily 도메인 테스트")
class ProductRankDailyTest {

    @Test
    fun `일간 랭킹을 생성할 수 있다`() {
        // given
        val rankingDate = LocalDate.of(2025, 1, 15)
        val productId = 100L
        val score = 150.5
        val rank = 1

        // when
        val productRankDaily = ProductRankDaily(
            rankingDate = rankingDate,
            productId = productId,
            score = score,
            rank = rank,
        )

        // then
        assertThat(productRankDaily.rankingDate).isEqualTo(rankingDate)
        assertThat(productRankDaily.productId).isEqualTo(productId)
        assertThat(productRankDaily.score).isEqualTo(score)
        assertThat(productRankDaily.rank).isEqualTo(rank)
        assertThat(productRankDaily.likeCount).isEqualTo(0)
        assertThat(productRankDaily.viewCount).isEqualTo(0)
        assertThat(productRankDaily.salesCount).isEqualTo(0)
    }

    @Test
    fun `메트릭 스냅샷을 포함한 일간 랭킹을 생성할 수 있다`() {
        // given
        val rankingDate = LocalDate.of(2025, 1, 15)
        val likeCount = 100L
        val viewCount = 5000L
        val salesCount = 50L

        // when
        val productRankDaily = ProductRankDaily(
            rankingDate = rankingDate,
            productId = 200L,
            score = 200.0,
            rank = 2,
            likeCount = likeCount,
            viewCount = viewCount,
            salesCount = salesCount,
        )

        // then
        assertThat(productRankDaily.likeCount).isEqualTo(likeCount)
        assertThat(productRankDaily.viewCount).isEqualTo(viewCount)
        assertThat(productRankDaily.salesCount).isEqualTo(salesCount)
    }
}
