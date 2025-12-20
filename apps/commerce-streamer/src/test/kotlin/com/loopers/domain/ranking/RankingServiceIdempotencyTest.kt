package com.loopers.domain.ranking

import com.loopers.IntegrationTest
import com.loopers.domain.event.EventHandledRepository
import com.loopers.domain.ranking.dto.LikeScoreEvent
import com.loopers.domain.ranking.dto.ViewScoreEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.ZonedDateTime

@DisplayName("RankingService 멱등성 테스트")
class RankingServiceIdempotencyTest : IntegrationTest() {

    @Autowired
    private lateinit var rankingService: RankingService

    @Autowired
    private lateinit var rankingRepository: RankingRepository

    @Autowired
    private lateinit var eventHandledRepository: EventHandledRepository

    companion object {
        private const val RANKING_LIKE_CONSUMER_GROUP = "ranking-like-consumer"
        private const val RANKING_ORDER_CONSUMER_GROUP = "ranking-order-consumer"
    }

    @Test
    @DisplayName("조회수 배치에서 동일 이벤트가 중복되어도 1번만 반영된다")
    fun testDuplicateViewEventsBatchWhenProcessedThenIdempotent() {
        // given
        val productId = 6L
        val dateKey = "20251220"
        val eventId = "ranking-view-events-0-5001"
        val eventType = "VIEW_COUNT_INCREASED"
        val eventTimestamp = ZonedDateTime.now()
        val events = listOf(
            ViewScoreEvent(productId, dateKey, eventId, eventType, eventTimestamp),
            ViewScoreEvent(productId, dateKey, eventId, eventType, eventTimestamp.plusSeconds(1)),
            ViewScoreEvent(productId, dateKey, eventId, eventType, eventTimestamp.plusSeconds(2)),
        )

        // when
        rankingService.incrementViewScoreBatch(events)

        // then
        val score = rankingRepository.getScore(dateKey, productId)
        assertThat(score).isEqualTo(0.1)
    }

    @Test
    @DisplayName("좋아요 배치에서 동일 이벤트가 중복되어도 1번만 반영된다")
    fun testDuplicateLikeEventsBatchWhenProcessedThenIdempotent() {
        // given
        val productId = 7L
        val dateKey = "20251220"
        val eventId = "ranking-like-events-0-6001"
        val eventType = "LIKE_COUNT_CHANGED"
        val eventTimestamp = ZonedDateTime.now()
        val events = listOf(
            LikeScoreEvent(productId, dateKey, eventId, eventType, eventTimestamp),
            LikeScoreEvent(productId, dateKey, eventId, eventType, eventTimestamp.plusSeconds(1)),
        )

        // when
        rankingService.incrementLikeScoreBatch(events, RANKING_LIKE_CONSUMER_GROUP)

        // then
        val score = rankingRepository.getScore(dateKey, productId)
        assertThat(score).isEqualTo(0.2)
    }
}
