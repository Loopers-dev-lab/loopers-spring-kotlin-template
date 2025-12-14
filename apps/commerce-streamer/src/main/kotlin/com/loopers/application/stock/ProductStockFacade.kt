package com.loopers.application.stock

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.cache.ProductCache
import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.EventHandledRepository
import com.loopers.domain.event.OutboxEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductStockFacade(
    private val eventHandledRepository: EventHandledRepository,
    private val productCache: ProductCache,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(ProductStockFacade::class.java)

    /**
     * 품절 이벤트 배치 처리
     */
    @Transactional
    fun handleSoldOutEvents(records: List<ConsumerRecord<Any, Any>>) {
        records.forEach { record ->
            val event = objectMapper.readValue(record.value() as String, OutboxEvent.SoldOut::class.java)
            val eventId = EventHandled.generateEventId(record.topic(), record.partition(), record.offset())

            // 멱등성 체크
            if (eventHandledRepository.existsById(eventId)) {
                log.debug("이미 처리된 품절 이벤트입니다: eventId={}", eventId)
                return
            }

            log.debug("품절 이벤트 처리: productId={}, eventId={}", event.productId, eventId)

            // 상품 상세 캐시 삭제 (모든 사용자)
            productCache.evictProductDetail(event.productId)

            // 이벤트 처리 기록
            eventHandledRepository.save(EventHandled.create(eventId, OutboxEvent.SoldOut.EVENT_TYPE, event.timestamp))

            log.debug("품절 이벤트 처리 완료: productId={}", event.productId)
        }
    }
}
