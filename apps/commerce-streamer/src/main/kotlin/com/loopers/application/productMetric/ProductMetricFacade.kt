package com.loopers.application.productMetric

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.event.EventHandleService
import com.loopers.domain.productMetric.ProductMetricService
import com.loopers.event.LikeEventPayload
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductMetricFacade(
    private val productMetricService: ProductMetricService,
    private val eventHandleService: EventHandleService,
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    fun updateLikeCount(messages: List<ConsumerRecord<String, String>>) {
        val eventIds = mutableSetOf<String>()
        val likeCountGroupBy = mutableMapOf<Long, Long>()

        messages.forEach { record ->
            val payload = objectMapper.readValue(record.value(), LikeEventPayload::class.java)
            val eventId = payload.eventId

            if (eventIds.contains(eventId)) {
                return@forEach
            }

            if (eventHandleService.duplicatedBy(eventId)) {
                return@forEach
            }

            likeCountGroupBy[payload.productId] = likeCountGroupBy.getOrPut(payload.productId) { 0L } +
                    if (payload.type == LikeEventPayload.LikeType.LIKED) 1L else -1L

            eventIds.add(eventId)
            eventHandleService.ensureLikeEvent(eventId)
        }

        productMetricService.updateLikeCount(likeCountGroupBy)
    }
}
