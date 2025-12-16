package com.loopers.domain.metrics

import com.loopers.IntegrationTest
import com.loopers.domain.event.EventHandledRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.ZonedDateTime

@DisplayName("ProductMetricsService 멱등성 테스트 - 중복 메시지 재전송 시 한 번만 반영")
class ProductMetricsServiceIdempotencyTest : IntegrationTest() {

    @Autowired
    private lateinit var productMetricsService: ProductMetricsService

    @Autowired
    private lateinit var productMetricsRepository: ProductMetricsRepository

    @Autowired
    private lateinit var eventHandledRepository: EventHandledRepository

    companion object {
        private const val LIKE_CONSUMER_GROUP = "product-metrics-like-consumer"
        private const val ORDER_CONSUMER_GROUP = "order-metrics-consumer"
    }

    @Test
    @DisplayName("동일한 좋아요 증가 이벤트를 3번 전송해도 1번만 반영된다")
    fun `duplicate like increase events should be processed only once`() {
        // given
        val productId = 1L
        val eventId = "product-like-events-0-12345"
        val eventType = "LikeCountChanged"
        val eventTimestamp = ZonedDateTime.now()

        // when - 동일한 이벤트를 3번 전송
        productMetricsService.increaseLikeCount(productId, eventId, eventType, eventTimestamp, LIKE_CONSUMER_GROUP)
        productMetricsService.increaseLikeCount(productId, eventId, eventType, eventTimestamp, LIKE_CONSUMER_GROUP)
        productMetricsService.increaseLikeCount(productId, eventId, eventType, eventTimestamp, LIKE_CONSUMER_GROUP)

        // then
        val metrics = productMetricsRepository.findByProductId(productId)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.likeCount).isEqualTo(1) // 3번 호출했지만 1만 증가

        // 이벤트 처리 기록이 1개만 있어야 함
        assertThat(eventHandledRepository.existsById(eventId)).isTrue()
    }

    @Test
    @DisplayName("동일한 조회수 증가 이벤트를 5번 전송해도 1번만 반영된다")
    fun `duplicate view increase events should be processed only once`() {
        // given
        val productId = 2L
        val eventId = "product-view-events-1-67890"
        val eventType = "ViewCountIncreased"
        val eventTimestamp = ZonedDateTime.now()

        // when - 동일한 이벤트를 5번 전송
        repeat(5) {
            productMetricsService.increaseViewCount(productId, eventId, eventType, eventTimestamp)
        }

        // then
        val metrics = productMetricsRepository.findByProductId(productId)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.viewCount).isEqualTo(1) // 5번 호출했지만 1만 증가
    }

    @Test
    @DisplayName("동일한 판매 수량 증가 이벤트를 3번 전송해도 1번만 반영된다")
    fun `duplicate sold count increase events should be processed only once`() {
        // given
        val productId = 3L
        val quantity = 5
        val eventId = "order-completed-events-0-11111"
        val eventType = "OrderCompleted"
        val eventTimestamp = ZonedDateTime.now()

        // when - 동일한 이벤트를 3번 전송
        productMetricsService.increaseSoldCount(productId, quantity, eventId, eventType, eventTimestamp, ORDER_CONSUMER_GROUP)
        productMetricsService.increaseSoldCount(productId, quantity, eventId, eventType, eventTimestamp, ORDER_CONSUMER_GROUP)
        productMetricsService.increaseSoldCount(productId, quantity, eventId, eventType, eventTimestamp, ORDER_CONSUMER_GROUP)

        // then
        val metrics = productMetricsRepository.findByProductId(productId)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.soldCount).isEqualTo(quantity.toLong()) // 3번 호출했지만 quantity만큼만 증가
    }

    @Test
    @DisplayName("서로 다른 이벤트는 각각 반영된다")
    fun `different events should be processed separately`() {
        // given
        val productId = 4L
        val eventId1 = "product-like-events-0"
        val eventId2 = "product-like-events-1"
        val eventId3 = "product-like-events-2"
        val eventType = "LikeCountChanged"
        val eventTimestamp = ZonedDateTime.now()

        // when - 서로 다른 이벤트를 3번 전송
        productMetricsService.increaseLikeCount(productId, eventId1, eventType, eventTimestamp, LIKE_CONSUMER_GROUP)
        productMetricsService.increaseLikeCount(productId, eventId2, eventType, eventTimestamp.plusSeconds(1L), LIKE_CONSUMER_GROUP)
        productMetricsService.increaseLikeCount(productId, eventId3, eventType, eventTimestamp.plusSeconds(2L), LIKE_CONSUMER_GROUP)

        // then - 3번 모두 반영되어야 함
        val metrics = productMetricsRepository.findByProductId(productId)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.likeCount).isEqualTo(3)
    }

    @Test
    @DisplayName("좋아요 증가/감소 이벤트가 중복 전송되어도 각각 1번씩만 반영된다")
    fun `duplicate like increase and decrease events should be processed only once each`() {
        // given
        val productId = 5L
        val increaseEventId = "product-like-events-0-2001"
        val decreaseEventId = "product-like-events-0-2002"
        val eventType = "LikeCountChanged"
        val eventTimestamp = ZonedDateTime.now()

        // when - 증가 이벤트 3번, 감소 이벤트 3번
        repeat(3) {
            productMetricsService.increaseLikeCount(productId, increaseEventId, eventType, eventTimestamp, LIKE_CONSUMER_GROUP)
        }
        repeat(3) {
            productMetricsService.decreaseLikeCount(productId, decreaseEventId, eventType, eventTimestamp.plusSeconds(1), LIKE_CONSUMER_GROUP)
        }

        // then - 증가 1번, 감소 1번만 반영 (최종 0)
        val metrics = productMetricsRepository.findByProductId(productId)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.likeCount).isEqualTo(0) // +1 -1 = 0
    }

    @Test
    @DisplayName("주문 완료/취소 이벤트가 중복 전송되어도 각각 1번씩만 반영된다")
    fun `duplicate order completed and canceled events should be processed only once each`() {
        // given
        val productId = 6L
        val quantity = 10
        val completedEventId = "order-completed-events-0-3001"
        val canceledEventId = "order-canceled-events-0-3002"
        val completedEventType = "OrderCompleted"
        val canceledEventType = "OrderCanceled"
        val eventTimestamp = ZonedDateTime.now()

        // when - 완료 이벤트 3번, 취소 이벤트 3번
        repeat(3) {
            productMetricsService.increaseSoldCount(productId, quantity, completedEventId, completedEventType, eventTimestamp, ORDER_CONSUMER_GROUP)
        }
        repeat(3) {
            productMetricsService.decreaseSoldCount(productId, quantity, canceledEventId, canceledEventType, eventTimestamp.plusSeconds(1), ORDER_CONSUMER_GROUP)
        }

        // then - 증가 1번, 감소 1번만 반영 (최종 0)
        val metrics = productMetricsRepository.findByProductId(productId)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.soldCount).isEqualTo(0) // +10 -10 = 0
    }

    @Test
    @DisplayName("다양한 이벤트가 섞여서 중복 전송되어도 각각 한 번씩만 반영된다")
    fun `mixed duplicate events should be processed only once each`() {
        // given
        val productId = 7L
        val likeEventId = "product-like-events-0-4001"
        val viewEventId = "product-view-events-0-4002"
        val soldEventId = "order-completed-events-0-4003"
        val baseTimestamp = ZonedDateTime.now()

        // when - 각 이벤트를 여러 번 전송
        repeat(2) {
            productMetricsService.increaseLikeCount(productId, likeEventId, "LikeCountChanged", baseTimestamp, LIKE_CONSUMER_GROUP)
            productMetricsService.increaseViewCount(productId, viewEventId, "ViewCountIncreased", baseTimestamp.plusSeconds(1))
            productMetricsService.increaseSoldCount(productId, 3, soldEventId, "OrderCompleted", baseTimestamp.plusSeconds(2), ORDER_CONSUMER_GROUP)
        }

        // then - 각각 1번씩만 반영
        val metrics = productMetricsRepository.findByProductId(productId)
        assertThat(metrics).isNotNull
        assertThat(metrics!!.likeCount).isEqualTo(1)
        assertThat(metrics.viewCount).isEqualTo(1)
        assertThat(metrics.soldCount).isEqualTo(3)
    }
}
