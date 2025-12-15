package com.loopers.application.metrics

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.EventHandledRepository
import com.loopers.domain.event.EventProcessingTimestamp
import com.loopers.domain.event.EventProcessingTimestampRepository
import com.loopers.domain.event.OutboxEvent
import com.loopers.domain.metrics.ProductMetrics
import com.loopers.domain.metrics.ProductMetricsRepository
import com.loopers.support.util.EventIdExtractor
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Service
class ProductMetricsFacade(
    private val productMetricsRepository: ProductMetricsRepository,
    private val eventHandledRepository: EventHandledRepository,
    private val eventProcessingTimestampRepository: EventProcessingTimestampRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(ProductMetricsFacade::class.java)

    @Transactional
    fun increaseLikeCount(
        productId: Long,
        eventId: String,
        eventType: String,
        eventTimestamp: ZonedDateTime,
        consumerGroup: String,
    ) {
        val aggregateId = productId.toString()
        // 멱등성 체크
        if (eventHandledRepository.existsById(eventId)) {
            log.debug("이미 처리된 이벤트입니다: eventId={}", eventId)
            return
        }

        // 순서 체크: 과거 이벤트인지 확인
        val lastProcessed = eventProcessingTimestampRepository.findByConsumerGroupAndAggregateId(consumerGroup, aggregateId)
        if (lastProcessed != null && !lastProcessed.shouldProcess(eventTimestamp)) {
            log.warn(
                "오래된 이벤트 무시: productId={}, eventTimestamp={}, lastProcessedAt={}",
                productId,
                eventTimestamp,
                lastProcessed.lastProcessedAt,
            )
            // 무시한 이벤트도 처리 완료로 마킹 (재처리 방지)
            eventHandledRepository.save(EventHandled.create(eventId, eventType, eventTimestamp))
            return
        }

        val metrics = productMetricsRepository.findByProductIdWithLock(productId)
            ?: ProductMetrics.create(productId)

        metrics.increaseLikeCount()
        productMetricsRepository.save(metrics)

        // 마지막 처리 시간 업데이트
        if (lastProcessed == null) {
            eventProcessingTimestampRepository.save(
                EventProcessingTimestamp.create(consumerGroup, aggregateId, eventTimestamp),
            )
        } else {
            lastProcessed.updateLastProcessedAt(eventTimestamp)
            eventProcessingTimestampRepository.save(lastProcessed)
        }

        // 이벤트 처리 기록
        eventHandledRepository.save(EventHandled.create(eventId, eventType, eventTimestamp))

        log.info("좋아요 수 증가: productId={}, likeCount={}", productId, metrics.likeCount)
    }

    @Transactional
    fun decreaseLikeCount(
        productId: Long,
        eventId: String,
        eventType: String,
        eventTimestamp: ZonedDateTime,
        consumerGroup: String,
    ) {
        val aggregateId = productId.toString()
        // 멱등성 체크
        if (eventHandledRepository.existsById(eventId)) {
            log.debug("이미 처리된 이벤트입니다: eventId={}", eventId)
            return
        }

        // 순서 체크
        val lastProcessed = eventProcessingTimestampRepository.findByConsumerGroupAndAggregateId(consumerGroup, aggregateId)
        if (lastProcessed != null && !lastProcessed.shouldProcess(eventTimestamp)) {
            log.warn(
                "오래된 이벤트 무시: productId={}, eventTimestamp={}, lastProcessedAt={}",
                productId,
                eventTimestamp,
                lastProcessed.lastProcessedAt,
            )
            eventHandledRepository.save(EventHandled.create(eventId, eventType, eventTimestamp))
            return
        }

        val metrics = productMetricsRepository.findByProductIdWithLock(productId)
            ?: ProductMetrics.create(productId)

        metrics.decreaseLikeCount()
        productMetricsRepository.save(metrics)

        // 마지막 처리 시간 업데이트
        if (lastProcessed == null) {
            eventProcessingTimestampRepository.save(
                EventProcessingTimestamp.create(consumerGroup, aggregateId, eventTimestamp),
            )
        } else {
            lastProcessed.updateLastProcessedAt(eventTimestamp)
            eventProcessingTimestampRepository.save(lastProcessed)
        }

        // 이벤트 처리 기록
        eventHandledRepository.save(EventHandled.create(eventId, eventType, eventTimestamp))

        log.info("좋아요 수 감소: productId={}, likeCount={}", productId, metrics.likeCount)
    }

    @Transactional
    fun increaseViewCount(
        productId: Long,
        eventId: String,
        eventType: String,
        eventTimestamp: ZonedDateTime,
        consumerGroup: String,
    ) {
        val aggregateId = productId.toString()
        // 멱등성 체크
        if (eventHandledRepository.existsById(eventId)) {
            log.debug("이미 처리된 이벤트입니다: eventId={}", eventId)
            return
        }

        // 순서 체크
        val lastProcessed = eventProcessingTimestampRepository.findByConsumerGroupAndAggregateId(consumerGroup, aggregateId)
        if (lastProcessed != null && !lastProcessed.shouldProcess(eventTimestamp)) {
            log.warn(
                "오래된 이벤트 무시: productId={}, eventTimestamp={}, lastProcessedAt={}",
                productId,
                eventTimestamp,
                lastProcessed.lastProcessedAt,
            )
            eventHandledRepository.save(EventHandled.create(eventId, eventType, eventTimestamp))
            return
        }

        val metrics = productMetricsRepository.findByProductIdWithLock(productId)
            ?: ProductMetrics.create(productId)

        metrics.increaseViewCount()
        productMetricsRepository.save(metrics)

        // 마지막 처리 시간 업데이트
        if (lastProcessed == null) {
            eventProcessingTimestampRepository.save(
                EventProcessingTimestamp.create(consumerGroup, aggregateId, eventTimestamp),
            )
        } else {
            lastProcessed.updateLastProcessedAt(eventTimestamp)
            eventProcessingTimestampRepository.save(lastProcessed)
        }

        // 이벤트 처리 기록
        eventHandledRepository.save(EventHandled.create(eventId, eventType, eventTimestamp))

        log.debug("조회 수 증가: productId={}, viewCount={}", productId, metrics.viewCount)
    }

    /**
     * 판매 수량 증가 (주문 완료)
     */
    @Transactional
    fun increaseSoldCount(
        productId: Long,
        quantity: Int,
        eventId: String,
        eventType: String,
        eventTimestamp: ZonedDateTime,
        consumerGroup: String,
    ) {
        val aggregateId = productId.toString()
        // 멱등성 체크
        if (eventHandledRepository.existsById(eventId)) {
            log.debug("이미 처리된 이벤트입니다: eventId={}", eventId)
            return
        }

        // 순서 체크
        val lastProcessed = eventProcessingTimestampRepository.findByConsumerGroupAndAggregateId(consumerGroup, aggregateId)
        if (lastProcessed != null && !lastProcessed.shouldProcess(eventTimestamp)) {
            log.warn(
                "오래된 이벤트 무시: productId={}, eventTimestamp={}, lastProcessedAt={}",
                productId,
                eventTimestamp,
                lastProcessed.lastProcessedAt,
            )
            eventHandledRepository.save(EventHandled.create(eventId, eventType, eventTimestamp))
            return
        }

        val metrics = productMetricsRepository.findByProductIdWithLock(productId)
            ?: ProductMetrics.create(productId)

        metrics.increaseSoldCount(quantity)
        productMetricsRepository.save(metrics)

        // 마지막 처리 시간 업데이트
        if (lastProcessed == null) {
            eventProcessingTimestampRepository.save(
                EventProcessingTimestamp.create(consumerGroup, aggregateId, eventTimestamp),
            )
        } else {
            lastProcessed.updateLastProcessedAt(eventTimestamp)
            eventProcessingTimestampRepository.save(lastProcessed)
        }

        // 이벤트 처리 기록
        eventHandledRepository.save(EventHandled.create(eventId, eventType, eventTimestamp))

        log.info("판매 수량 증가: productId={}, quantity={}, soldCount={}", productId, quantity, metrics.soldCount)
    }

    /**
     * 판매 수량 감소 (주문 취소)
     */
    @Transactional
    fun decreaseSoldCount(
        productId: Long,
        quantity: Int,
        eventId: String,
        eventType: String,
        eventTimestamp: ZonedDateTime,
        consumerGroup: String,
    ) {
        val aggregateId = productId.toString()
        // 멱등성 체크
        if (eventHandledRepository.existsById(eventId)) {
            log.debug("이미 처리된 이벤트입니다: eventId={}", eventId)
            return
        }

        // 순서 체크
        val lastProcessed = eventProcessingTimestampRepository.findByConsumerGroupAndAggregateId(consumerGroup, aggregateId)
        if (lastProcessed != null && !lastProcessed.shouldProcess(eventTimestamp)) {
            log.warn(
                "오래된 이벤트 무시: productId={}, eventTimestamp={}, lastProcessedAt={}",
                productId,
                eventTimestamp,
                lastProcessed.lastProcessedAt,
            )
            eventHandledRepository.save(EventHandled.create(eventId, eventType, eventTimestamp))
            return
        }

        val metrics = productMetricsRepository.findByProductIdWithLock(productId)
            ?: ProductMetrics.create(productId)

        metrics.decreaseSoldCount(quantity)
        productMetricsRepository.save(metrics)

        // 마지막 처리 시간 업데이트
        if (lastProcessed == null) {
            eventProcessingTimestampRepository.save(
                EventProcessingTimestamp.create(consumerGroup, aggregateId, eventTimestamp),
            )
        } else {
            lastProcessed.updateLastProcessedAt(eventTimestamp)
            eventProcessingTimestampRepository.save(lastProcessed)
        }

        // 이벤트 처리 기록
        eventHandledRepository.save(EventHandled.create(eventId, eventType, eventTimestamp))

        log.info("판매 수량 감소: productId={}, quantity={}, soldCount={}", productId, quantity, metrics.soldCount)
    }

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
                    increaseLikeCount(
                        productId = event.productId,
                        eventId = eventId,
                        eventType = OutboxEvent.LikeCountChanged.EVENT_TYPE,
                        eventTimestamp = event.timestamp,
                        consumerGroup = consumerGroup,
                    )
                }

                OutboxEvent.LikeCountChanged.LikeAction.UNLIKED -> {
                    decreaseLikeCount(
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
    fun handleViewEvents(records: List<ConsumerRecord<Any, Any>>, consumerGroup: String) {
        records.forEach { record ->
            val event = objectMapper.readValue(record.value() as String, OutboxEvent.ViewCountIncreased::class.java)
            val eventId = EventIdExtractor.extract(record)

            log.debug("조회수 이벤트 처리: productId={}, eventId={}", event.productId, eventId)

            increaseViewCount(
                productId = event.productId,
                eventId = eventId,
                eventType = OutboxEvent.ViewCountIncreased.EVENT_TYPE,
                eventTimestamp = event.timestamp,
                consumerGroup = consumerGroup,
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
                increaseSoldCount(
                    productId = item.productId,
                    quantity = item.quantity,
                    eventId = "$eventId-${item.productId}",
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
                decreaseSoldCount(
                    productId = item.productId,
                    quantity = item.quantity,
                    eventId = "$eventId-${item.productId}",
                    eventType = OutboxEvent.OrderCanceled.EVENT_TYPE,
                    eventTimestamp = event.timestamp,
                    consumerGroup = consumerGroup,
                )
            }
        }
    }
}
