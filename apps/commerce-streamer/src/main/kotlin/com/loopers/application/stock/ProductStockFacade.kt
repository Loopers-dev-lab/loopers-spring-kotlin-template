package com.loopers.application.stock

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.event.OutboxEvent
import com.loopers.domain.stock.ProductStockService
import com.loopers.support.util.EventIdExtractor
import com.loopers.support.util.readEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ProductStockFacade(
    private val productStockService: ProductStockService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(ProductStockFacade::class.java)

    /**
     * 품절 이벤트 배치 처리
     */
    fun handleSoldOutEvents(records: List<ConsumerRecord<Any, Any>>) {
        records.forEach { record ->
            val event = readEvent<OutboxEvent.SoldOut>(record, objectMapper)
            val eventId = EventIdExtractor.extract(record)

            log.debug("품절 이벤트 처리: productId={}, eventId={}", event.productId, eventId)

            productStockService.handleSoldOut(
                productId = event.productId,
                eventId = eventId,
                eventType = OutboxEvent.SoldOut.EVENT_TYPE,
                eventTimestamp = event.timestamp,
            )
        }
    }
}
