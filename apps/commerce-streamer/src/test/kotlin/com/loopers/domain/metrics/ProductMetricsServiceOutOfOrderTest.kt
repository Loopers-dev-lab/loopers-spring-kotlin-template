package com.loopers.domain.metrics

import com.loopers.IntegrationTest
import com.loopers.domain.event.EventHandledRepository
import com.loopers.domain.event.EventProcessingTimestampRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.ZonedDateTime

@DisplayName("ProductMetricsService 순서 보장 테스트 - 늦게 온 메시지 무시")
class ProductMetricsServiceOutOfOrderTest : IntegrationTest() {

    @Autowired
    private lateinit var productMetricsService: ProductMetricsService

    @Autowired
    private lateinit var productMetricsRepository: ProductMetricsRepository

    @Autowired
    private lateinit var eventHandledRepository: EventHandledRepository

    @Autowired
    private lateinit var eventProcessingTimestampRepository: EventProcessingTimestampRepository

    companion object {
        private const val LIKE_CONSUMER_GROUP = "product-metrics-like-consumer"
        private const val ORDER_CONSUMER_GROUP = "order-metrics-consumer"
    }

    @Test
    @DisplayName("최신 이벤트 처리 후 과거 이벤트가 도착하면 무시된다")
    fun `old event should be ignored after processing newer event`() {
        // given
        val productId = 1L
        val now = ZonedDateTime.now()
        val metricDate = now.toLocalDate()
        val oldEventId = "product-like-events-0-1001"
        val newEventId = "product-like-events-0-1002"
        val eventType = "LikeCountChanged"

        // when - 최신 이벤트를 먼저 처리
        productMetricsService.increaseLikeCount(productId, newEventId, eventType, now.plusMinutes(10), LIKE_CONSUMER_GROUP)

        // then - 과거 이벤트는 무시됨
        productMetricsService.increaseLikeCount(productId, oldEventId, eventType, now, LIKE_CONSUMER_GROUP)

        val metrics = productMetricsRepository.findByProductIdAndMetricDate(productId, metricDate)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.likeCount).isEqualTo(1) // 최신 이벤트 1번만 반영

        // 과거 이벤트도 event_handled에는 기록됨 (중복 처리 방지)
        val aggregateId = "${productId}_$metricDate"
        assertThat(eventHandledRepository.existsByEventIdAndAggregateId(oldEventId, aggregateId)).isTrue()
        assertThat(eventHandledRepository.existsByEventIdAndAggregateId(newEventId, aggregateId)).isTrue()
    }

    @Test
    @DisplayName("시간 순서대로 도착하면 모두 반영된다")
    fun `events in chronological order should be processed`() {
        // given
        val productId = 2L
        val now = ZonedDateTime.now()
        val metricDate = now.toLocalDate()
        val event1Id = "product-like-events-0-2001"
        val event2Id = "product-like-events-0-2002"
        val event3Id = "product-like-events-0-2003"
        val eventType = "LikeCountChanged"

        // when - 시간 순서대로 이벤트 처리
        productMetricsService.increaseLikeCount(productId, event1Id, eventType, now, LIKE_CONSUMER_GROUP)
        productMetricsService.increaseLikeCount(productId, event2Id, eventType, now.plusSeconds(1), LIKE_CONSUMER_GROUP)
        productMetricsService.increaseLikeCount(productId, event3Id, eventType, now.plusSeconds(2), LIKE_CONSUMER_GROUP)

        // then - 3개 모두 반영
        val metrics = productMetricsRepository.findByProductIdAndMetricDate(productId, metricDate)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.likeCount).isEqualTo(3)
    }

    @Test
    @DisplayName("역순으로 도착하면 첫 번째(가장 최신) 이벤트만 반영된다")
    fun `only the first newest event should be processed when events arrive in reverse order`() {
        // given
        val productId = 3L
        val now = ZonedDateTime.now()
        val metricDate = now.toLocalDate()
        val oldestEventId = "product-like-events-0-3001"
        val middleEventId = "product-like-events-0-3002"
        val newestEventId = "product-like-events-0-3003"
        val eventType = "LikeCountChanged"

        // when - 역순으로 이벤트 도착 (최신 → 과거)
        productMetricsService.increaseLikeCount(productId, newestEventId, eventType, now.plusSeconds(10), LIKE_CONSUMER_GROUP)
        productMetricsService.increaseLikeCount(productId, middleEventId, eventType, now.plusSeconds(5), LIKE_CONSUMER_GROUP)
        productMetricsService.increaseLikeCount(productId, oldestEventId, eventType, now, LIKE_CONSUMER_GROUP)

        // then - 첫 번째 이벤트(가장 최신)만 반영
        val metrics = productMetricsRepository.findByProductIdAndMetricDate(productId, metricDate)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.likeCount).isEqualTo(1)
    }

    @Test
    @DisplayName("무작위 순서로 도착한 이벤트 중 가장 최신 이벤트까지만 반영된다")
    fun `events arriving in random order should process only up to the latest timestamp`() {
        // given
        val productId = 4L
        val now = ZonedDateTime.now()
        val metricDate = now.toLocalDate()
        val eventType = "LikeCountChanged"

        // when - 무작위 순서로 이벤트 도착: t+5, t+1, t+10, t+3, t+7
        productMetricsService.increaseLikeCount(productId, "event-1", eventType, now.plusSeconds(5), LIKE_CONSUMER_GROUP) // 반영됨
        productMetricsService.increaseLikeCount(productId, "event-2", eventType, now.plusSeconds(1), LIKE_CONSUMER_GROUP) // 무시됨 (t+5보다 과거)
        productMetricsService.increaseLikeCount(productId, "event-3", eventType, now.plusSeconds(10), LIKE_CONSUMER_GROUP) // 반영됨 (t+5보다 최신)
        productMetricsService.increaseLikeCount(productId, "event-4", eventType, now.plusSeconds(3), LIKE_CONSUMER_GROUP) // 무시됨 (t+10보다 과거)
        productMetricsService.increaseLikeCount(productId, "event-5", eventType, now.plusSeconds(7), LIKE_CONSUMER_GROUP) // 무시됨 (t+10보다 과거)

        // then - event-1(t+5), event-3(t+10) 2개만 반영
        val metrics = productMetricsRepository.findByProductIdAndMetricDate(productId, metricDate)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.likeCount).isEqualTo(2)
    }

    @Test
    @DisplayName("판매 수량 증가 이벤트도 시간 순서가 보장된다")
    fun `sold count events should respect time ordering`() {
        // given
        val productId = 6L
        val now = ZonedDateTime.now()
        val metricDate = now.toLocalDate()
        val eventType = "OrderCompleted"

        // when - 시간 역순으로 도착
        productMetricsService.increaseSoldCount(productId, 5, "order-1", eventType, now.plusHours(2), ORDER_CONSUMER_GROUP)
        productMetricsService.increaseSoldCount(productId, 3, "order-2", eventType, now.plusHours(1), ORDER_CONSUMER_GROUP) // 무시
        productMetricsService.increaseSoldCount(productId, 2, "order-3", eventType, now.plusHours(3), ORDER_CONSUMER_GROUP) // 반영

        // then
        val metrics = productMetricsRepository.findByProductIdAndMetricDate(productId, metricDate)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.soldCount).isEqualTo(7) // order-1(5) + order-3(2) = 7
    }

    @Test
    @DisplayName("좋아요 증가/감소 이벤트도 시간 순서가 보장된다")
    fun `like increase and decrease events should respect time ordering`() {
        // given
        val productId = 7L
        val now = ZonedDateTime.now()
        val metricDate = now.toLocalDate()
        val eventType = "LikeCountChanged"

        // when - 복잡한 시나리오
        productMetricsService.increaseLikeCount(productId, "like-1", eventType, now.plusMinutes(5), LIKE_CONSUMER_GROUP) // +1 (반영)
        productMetricsService.increaseLikeCount(productId, "like-2", eventType, now.plusMinutes(10), LIKE_CONSUMER_GROUP) // +1 (반영)
        productMetricsService.decreaseLikeCount(productId, "unlike-1", eventType, now.plusMinutes(3), LIKE_CONSUMER_GROUP) // -1 (무시, t+10보다 과거)
        productMetricsService.decreaseLikeCount(productId, "unlike-2", eventType, now.plusMinutes(15), LIKE_CONSUMER_GROUP) // -1 (반영)
        productMetricsService.increaseLikeCount(productId, "like-3", eventType, now.plusMinutes(7), LIKE_CONSUMER_GROUP) // +1 (무시, t+15보다 과거)

        // then - like-1(+1) + like-2(+1) + unlike-2(-1) = 1
        val metrics = productMetricsRepository.findByProductIdAndMetricDate(productId, metricDate)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.likeCount).isEqualTo(1)
    }

    @Test
    @DisplayName("동일한 시간의 이벤트는 첫 번째만 반영되고 나머지는 무시된다")
    fun `events with same timestamp should process only the first one`() {
        // given
        val productId = 8L
        val now = ZonedDateTime.now()
        val metricDate = now.toLocalDate()
        val eventType = "LikeCountChanged"

        // when - 동일한 타임스탬프로 여러 이벤트 (서로 다른 eventId)
        productMetricsService.increaseLikeCount(productId, "like-1", eventType, now, LIKE_CONSUMER_GROUP)
        productMetricsService.increaseLikeCount(productId, "like-2", eventType, now, LIKE_CONSUMER_GROUP)
        productMetricsService.increaseLikeCount(productId, "like-3", eventType, now, LIKE_CONSUMER_GROUP)

        // then - 첫 번째만 반영됨 (같은 시간은 isAfter가 false이므로 무시됨)
        val metrics = productMetricsRepository.findByProductIdAndMetricDate(productId, metricDate)
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
        val metricDate = now.toLocalDate()
        val eventType = "LikeCountChanged"

        // when - 상품1은 최신 이벤트 먼저, 상품2는 과거 이벤트 먼저
        productMetricsService.increaseLikeCount(productId1, "p1-new", eventType, now.plusMinutes(10), LIKE_CONSUMER_GROUP)
        productMetricsService.increaseLikeCount(productId1, "p1-old", eventType, now, LIKE_CONSUMER_GROUP) // 무시됨

        productMetricsService.increaseLikeCount(productId2, "p2-old", eventType, now, LIKE_CONSUMER_GROUP)
        productMetricsService.increaseLikeCount(productId2, "p2-new", eventType, now.plusMinutes(10), LIKE_CONSUMER_GROUP) // 반영됨

        // then
        val metrics1 = productMetricsRepository.findByProductIdAndMetricDate(productId1, metricDate)
        assertThat(metrics1!!.likeCount).isEqualTo(1)

        val metrics2 = productMetricsRepository.findByProductIdAndMetricDate(productId2, metricDate)
        assertThat(metrics2!!.likeCount).isEqualTo(2)
    }

    @Test
    @DisplayName("EventProcessingTimestamp가 업데이트되는지 확인")
    fun `EventProcessingTimestamp should be updated with latest processed event`() {
        // given
        val productId = 11L
        val now = ZonedDateTime.now()
        val metricDate = now.toLocalDate()
        val eventType = "LikeCountChanged"

        // when
        productMetricsService.increaseLikeCount(productId, "event-1", eventType, now.plusMinutes(5), LIKE_CONSUMER_GROUP)
        productMetricsService.increaseLikeCount(productId, "event-2", eventType, now.plusMinutes(10), LIKE_CONSUMER_GROUP)

        // then - EventProcessingTimestamp가 최신 시간으로 업데이트됨
        val aggregateId = "${productId}_$metricDate"
        val timestamp = eventProcessingTimestampRepository.findByConsumerGroupAndAggregateId(LIKE_CONSUMER_GROUP, aggregateId)
        assertThat(timestamp).isNotNull
        assertThat(timestamp!!.lastProcessedAt).isEqualTo(now.plusMinutes(10))
    }
}
