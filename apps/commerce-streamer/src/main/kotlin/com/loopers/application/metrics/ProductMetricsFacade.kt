package com.loopers.application.metrics

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.event.OutboxEvent
import com.loopers.domain.metrics.ProductMetricsService
import com.loopers.support.util.EventIdExtractor
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ProductMetricsFacade(
    private val productMetricsService: ProductMetricsService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(ProductMetricsFacade::class.java)

    /**
     * 좋아요 이벤트 배치 처리
     */
    fun handleLikeEvents(records: List<ConsumerRecord<Any, Any>>, consumerGroup: String) {
        records.forEach { record ->
            val event = objectMapper.readValue(record.value() as String, OutboxEvent.LikeCountChanged::class.java)
            val eventId = EventIdExtractor.extract(record)

            log.debug(
                "좋아요 이벤트 처리: productId={}, action={}, eventId={}",
                event.productId,
                event.action,
                eventId,
            )

            when (event.action) {
                OutboxEvent.LikeCountChanged.LikeAction.LIKED -> {
                    productMetricsService.increaseLikeCount(
                        productId = event.productId,
                        eventId = eventId,
                        eventType = OutboxEvent.LikeCountChanged.EVENT_TYPE,
                        eventTimestamp = event.timestamp,
                        consumerGroup = consumerGroup,
                    )
                }

                OutboxEvent.LikeCountChanged.LikeAction.UNLIKED -> {
                    productMetricsService.decreaseLikeCount(
                        productId = event.productId,
                        eventId = eventId,
                        eventType = OutboxEvent.LikeCountChanged.EVENT_TYPE,
                        eventTimestamp = event.timestamp,
                        consumerGroup = consumerGroup,
                    )
                }
            }
        }
    }

    /**
     * 조회수 이벤트 배치 처리
     */
    fun handleViewEvents(records: List<ConsumerRecord<Any, Any>>) {
        records.forEach { record ->
            val event = objectMapper.readValue(record.value() as String, OutboxEvent.ViewCountIncreased::class.java)
            val eventId = EventIdExtractor.extract(record)

            log.debug("조회수 이벤트 처리: productId={}, eventId={}", event.productId, eventId)

            productMetricsService.increaseViewCount(
                productId = event.productId,
                eventId = eventId,
                eventType = OutboxEvent.ViewCountIncreased.EVENT_TYPE,
                eventTimestamp = event.timestamp,
            )
        }
    }

    /**
     * 주문 완료 이벤트 배치 처리
     */
    fun handleOrderCompletedEvents(records: List<ConsumerRecord<Any, Any>>, consumerGroup: String) {
        records.forEach { record ->
            val event = objectMapper.readValue(record.value() as String, OutboxEvent.OrderCompleted::class.java)
            val eventId = EventIdExtractor.extract(record)

            log.debug(
                "주문 완료 이벤트 처리: orderId={}, items={}, eventId={}",
                event.orderId,
                event.items.size,
                eventId,
            )

            // 주문의 각 상품별로 판매 수량 증가
            event.items.forEach { item ->
                productMetricsService.increaseSoldCount(
                    productId = item.productId,
                    quantity = item.quantity,
                    eventId = eventId,
                    eventType = OutboxEvent.OrderCompleted.EVENT_TYPE,
                    eventTimestamp = event.timestamp,
                    consumerGroup = consumerGroup,
                )
            }
        }
    }

    /**
     * 주문 취소 이벤트 배치 처리
     */
    fun handleOrderCanceledEvents(records: List<ConsumerRecord<Any, Any>>, consumerGroup: String) {
        records.forEach { record ->
            val event = objectMapper.readValue(record.value() as String, OutboxEvent.OrderCanceled::class.java)
            val eventId = EventIdExtractor.extract(record)

            log.debug(
                "주문 취소 이벤트 처리: orderId={}, items={}, reason={}, eventId={}",
                event.orderId,
                event.items.size,
                event.reason,
                eventId,
            )

            // 주문의 각 상품별로 판매 수량 감소
            event.items.forEach { item ->
                productMetricsService.decreaseSoldCount(
                    productId = item.productId,
                    quantity = item.quantity,
                    eventId = eventId,
                    eventType = OutboxEvent.OrderCanceled.EVENT_TYPE,
                    eventTimestamp = event.timestamp,
                    consumerGroup = consumerGroup,
                )
            }
        }
    }
}
