package com.loopers.domain.ranking

import com.loopers.IntegrationTest
import com.loopers.domain.ranking.dto.LikeScoreEvent
import com.loopers.domain.ranking.dto.ViewScoreEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.ZonedDateTime

@DisplayName("RankingService 기능 테스트")
class RankingServiceTest : IntegrationTest() {

    @Autowired
    private lateinit var rankingService: RankingService

    @Autowired
    private lateinit var rankingRepository: RankingRepository

    @Test
    @DisplayName("조회수 배치 처리 시 상품별 점수가 누적된다")
    fun testViewScoreBatchWhenMultipleEventsThenAggregated() {
        // given
        val dateKey = "20251220"
        val timestamp = ZonedDateTime.now()
        val events = listOf(
            ViewScoreEvent(10L, dateKey, "view-1", "VIEW_COUNT_INCREASED", timestamp),
            ViewScoreEvent(10L, dateKey, "view-2", "VIEW_COUNT_INCREASED", timestamp.plusSeconds(1)),
            ViewScoreEvent(11L, dateKey, "view-3", "VIEW_COUNT_INCREASED", timestamp.plusSeconds(2)),
        )

        // when
        rankingService.incrementViewScoreBatch(events)

        // then
        assertThat(rankingRepository.getScore(dateKey, 10L)).isEqualTo(0.2)
        assertThat(rankingRepository.getScore(dateKey, 11L)).isEqualTo(0.1)
    }

    @Test
    @DisplayName("좋아요 배치 증가 후 배치 감소 시 점수가 정확히 상쇄된다")
    fun testLikeScoreBatchWhenIncrementAndDecrementThenCorrect() {
        // given
        val productId = 12L
        val dateKey = "20251220"
        val timestamp = ZonedDateTime.now()
        val likedEvents = listOf(
            LikeScoreEvent(productId, dateKey, "like-1", "LIKE_COUNT_CHANGED", timestamp),
        )
        val unlikedEvents = listOf(
            LikeScoreEvent(productId, dateKey, "unlike-1", "LIKE_COUNT_CHANGED", timestamp.plusSeconds(1)),
        )

        // when
        rankingService.incrementLikeScoreBatch(likedEvents)
        rankingService.decrementLikeScoreBatch(unlikedEvents)

        // then
        assertThat(rankingRepository.getScore(dateKey, productId)).isEqualTo(0.0)
    }
}
