package com.loopers.application.metrics

import com.loopers.IntegrationTest
import com.loopers.domain.event.EventHandledRepository
import com.loopers.domain.event.EventProcessingTimestampRepository
import com.loopers.domain.metrics.ProductMetricsRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.ZonedDateTime

@DisplayName("ProductMetricsFacade 순서 보장 테스트 - 늦게 온 메시지 무시")
class ProductMetricsFacadeOutOfOrderTest : IntegrationTest() {

    @Autowired
    private lateinit var productMetricsFacade: ProductMetricsFacade

    @Autowired
    private lateinit var productMetricsRepository: ProductMetricsRepository

    @Autowired
    private lateinit var eventHandledRepository: EventHandledRepository

    @Autowired
    private lateinit var eventProcessingTimestampRepository: EventProcessingTimestampRepository

    @Test
    @DisplayName("최신 이벤트 처리 후 과거 이벤트가 도착하면 무시된다")
    fun `old event should be ignored after processing newer event`() {
        // given
        val productId = 1L
        val now = ZonedDateTime.now()
        val oldEventId = "product-like-events-0-1001"
        val newEventId = "product-like-events-0-1002"
        val eventType = "LikeCountChanged"

        // when - 최신 이벤트를 먼저 처리
        productMetricsFacade.increaseLikeCount(productId, newEventId, eventType, now.plusMinutes(10))

        // then - 과거 이벤트는 무시됨
        productMetricsFacade.increaseLikeCount(productId, oldEventId, eventType, now)

        val metrics = productMetricsRepository.findByProductId(productId)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.likeCount).isEqualTo(1) // 최신 이벤트 1번만 반영

        // 과거 이벤트도 event_handled에는 기록됨 (중복 처리 방지)
        assertThat(eventHandledRepository.existsById(oldEventId)).isTrue()
        assertThat(eventHandledRepository.existsById(newEventId)).isTrue()
    }

    @Test
    @DisplayName("시간 순서대로 도착하면 모두 반영된다")
    fun `events in chronological order should be processed`() {
        // given
        val productId = 2L
        val now = ZonedDateTime.now()
        val event1Id = "product-like-events-0-2001"
        val event2Id = "product-like-events-0-2002"
        val event3Id = "product-like-events-0-2003"
        val eventType = "LikeCountChanged"

        // when - 시간 순서대로 이벤트 처리
        productMetricsFacade.increaseLikeCount(productId, event1Id, eventType, now)
        productMetricsFacade.increaseLikeCount(productId, event2Id, eventType, now.plusSeconds(1))
        productMetricsFacade.increaseLikeCount(productId, event3Id, eventType, now.plusSeconds(2))

        // then - 3개 모두 반영
        val metrics = productMetricsRepository.findByProductId(productId)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.likeCount).isEqualTo(3)
    }

    @Test
    @DisplayName("역순으로 도착하면 첫 번째(가장 최신) 이벤트만 반영된다")
    fun `only the first newest event should be processed when events arrive in reverse order`() {
        // given
        val productId = 3L
        val now = ZonedDateTime.now()
        val oldestEventId = "product-like-events-0-3001"
        val middleEventId = "product-like-events-0-3002"
        val newestEventId = "product-like-events-0-3003"
        val eventType = "LikeCountChanged"

        // when - 역순으로 이벤트 도착 (최신 → 과거)
        productMetricsFacade.increaseLikeCount(productId, newestEventId, eventType, now.plusSeconds(10))
        productMetricsFacade.increaseLikeCount(productId, middleEventId, eventType, now.plusSeconds(5))
        productMetricsFacade.increaseLikeCount(productId, oldestEventId, eventType, now)

        // then - 첫 번째 이벤트(가장 최신)만 반영
        val metrics = productMetricsRepository.findByProductId(productId)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.likeCount).isEqualTo(1)
    }

    @Test
    @DisplayName("무작위 순서로 도착한 이벤트 중 가장 최신 이벤트까지만 반영된다")
    fun `events arriving in random order should process only up to the latest timestamp`() {
        // given
        val productId = 4L
        val now = ZonedDateTime.now()
        val eventType = "LikeCountChanged"

        // when - 무작위 순서로 이벤트 도착: t+5, t+1, t+10, t+3, t+7
        productMetricsFacade.increaseLikeCount(productId, "event-1", eventType, now.plusSeconds(5)) // 반영됨
        productMetricsFacade.increaseLikeCount(productId, "event-2", eventType, now.plusSeconds(1)) // 무시됨 (t+5보다 과거)
        productMetricsFacade.increaseLikeCount(productId, "event-3", eventType, now.plusSeconds(10)) // 반영됨 (t+5보다 최신)
        productMetricsFacade.increaseLikeCount(productId, "event-4", eventType, now.plusSeconds(3)) // 무시됨 (t+10보다 과거)
        productMetricsFacade.increaseLikeCount(productId, "event-5", eventType, now.plusSeconds(7)) // 무시됨 (t+10보다 과거)

        // then - event-1(t+5), event-3(t+10) 2개만 반영
        val metrics = productMetricsRepository.findByProductId(productId)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.likeCount).isEqualTo(2)
    }

    @Test
    @DisplayName("조회수 증가 이벤트도 시간 순서가 보장된다")
    fun `view count events should respect time ordering`() {
        // given
        val productId = 5L
        val now = ZonedDateTime.now()
        val eventType = "ViewCountIncreased"

        // when - 최신 이벤트 먼저 처리 후 과거 이벤트 도착
        productMetricsFacade.increaseViewCount(productId, "view-1", eventType, now.plusMinutes(5))
        productMetricsFacade.increaseViewCount(productId, "view-2", eventType, now.plusMinutes(10)) // 반영
        productMetricsFacade.increaseViewCount(productId, "view-3", eventType, now.plusMinutes(3)) // 무시

        // then
        val metrics = productMetricsRepository.findByProductId(productId)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.viewCount).isEqualTo(2) // view-1, view-2만 반영
    }

    @Test
    @DisplayName("판매 수량 증가 이벤트도 시간 순서가 보장된다")
    fun `sold count events should respect time ordering`() {
        // given
        val productId = 6L
        val now = ZonedDateTime.now()
        val eventType = "OrderCompleted"

        // when - 시간 역순으로 도착
        productMetricsFacade.increaseSoldCount(productId, 5, "order-1", eventType, now.plusHours(2))
        productMetricsFacade.increaseSoldCount(productId, 3, "order-2", eventType, now.plusHours(1)) // 무시
        productMetricsFacade.increaseSoldCount(productId, 2, "order-3", eventType, now.plusHours(3)) // 반영

        // then
        val metrics = productMetricsRepository.findByProductId(productId)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.soldCount).isEqualTo(7) // order-1(5) + order-3(2) = 7
    }

    @Test
    @DisplayName("좋아요 증가/감소 이벤트도 시간 순서가 보장된다")
    fun `like increase and decrease events should respect time ordering`() {
        // given
        val productId = 7L
        val now = ZonedDateTime.now()
        val eventType = "LikeCountChanged"

        // when - 복잡한 시나리오
        productMetricsFacade.increaseLikeCount(productId, "like-1", eventType, now.plusMinutes(5)) // +1 (반영)
        productMetricsFacade.increaseLikeCount(productId, "like-2", eventType, now.plusMinutes(10)) // +1 (반영)
        productMetricsFacade.decreaseLikeCount(productId, "unlike-1", eventType, now.plusMinutes(3)) // -1 (무시, t+10보다 과거)
        productMetricsFacade.decreaseLikeCount(productId, "unlike-2", eventType, now.plusMinutes(15)) // -1 (반영)
        productMetricsFacade.increaseLikeCount(productId, "like-3", eventType, now.plusMinutes(7)) // +1 (무시, t+15보다 과거)

        // then - like-1(+1) + like-2(+1) + unlike-2(-1) = 1
        val metrics = productMetricsRepository.findByProductId(productId)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.likeCount).isEqualTo(1)
    }

    @Test
    @DisplayName("동일한 시간의 이벤트는 첫 번째만 반영되고 나머지는 무시된다")
    fun `events with same timestamp should process only the first one`() {
        // given
        val productId = 8L
        val now = ZonedDateTime.now()
        val eventType = "LikeCountChanged"

        // when - 동일한 타임스탬프로 여러 이벤트 (서로 다른 eventId)
        productMetricsFacade.increaseLikeCount(productId, "like-1", eventType, now)
        productMetricsFacade.increaseLikeCount(productId, "like-2", eventType, now)
        productMetricsFacade.increaseLikeCount(productId, "like-3", eventType, now)

        // then - 첫 번째만 반영됨 (같은 시간은 isAfter가 false이므로 무시됨)
        val metrics = productMetricsRepository.findByProductId(productId)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.likeCount).isEqualTo(1)
    }

    @Test
    @DisplayName("서로 다른 상품의 이벤트는 독립적으로 시간 순서가 관리된다")
    fun `events for different products should have independent time ordering`() {
        // given
        val productId1 = 9L
        val productId2 = 10L
        val now = ZonedDateTime.now()
        val eventType = "LikeCountChanged"

        // when - 상품1은 최신 이벤트 먼저, 상품2는 과거 이벤트 먼저
        productMetricsFacade.increaseLikeCount(productId1, "p1-new", eventType, now.plusMinutes(10))
        productMetricsFacade.increaseLikeCount(productId1, "p1-old", eventType, now) // 무시됨

        productMetricsFacade.increaseLikeCount(productId2, "p2-old", eventType, now)
        productMetricsFacade.increaseLikeCount(productId2, "p2-new", eventType, now.plusMinutes(10)) // 반영됨

        // then
        val metrics1 = productMetricsRepository.findByProductId(productId1)
        assertThat(metrics1!!.likeCount).isEqualTo(1)

        val metrics2 = productMetricsRepository.findByProductId(productId2)
        assertThat(metrics2!!.likeCount).isEqualTo(2)
    }

    @Test
    @DisplayName("EventProcessingTimestamp가 업데이트되는지 확인")
    fun `EventProcessingTimestamp should be updated with latest processed event`() {
        // given
        val productId = 11L
        val now = ZonedDateTime.now()
        val eventType = "LikeCountChanged"
        val consumerGroup = "product-metrics-consumer"

        // when
        productMetricsFacade.increaseLikeCount(productId, "event-1", eventType, now.plusMinutes(5))
        productMetricsFacade.increaseLikeCount(productId, "event-2", eventType, now.plusMinutes(10))

        // then - EventProcessingTimestamp가 최신 시간으로 업데이트됨
        val aggregateId = "product-$productId"
        val timestamp = eventProcessingTimestampRepository.findByConsumerGroupAndAggregateId(consumerGroup, aggregateId)
        assertThat(timestamp).isNotNull
        assertThat(timestamp!!.lastProcessedAt).isEqualTo(now.plusMinutes(10))
    }
}
