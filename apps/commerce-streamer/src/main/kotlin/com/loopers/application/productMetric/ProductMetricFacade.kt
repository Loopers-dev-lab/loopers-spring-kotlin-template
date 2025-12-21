package com.loopers.application.productMetric

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.event.EventHandleService
import com.loopers.domain.productMetric.ProductMetricService
import com.loopers.event.CatalogEventPayload
import com.loopers.event.CatalogType
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
    fun updateProductMetrics(messages: List<ConsumerRecord<String, String>>) {
        val commands = buildCommands(messages)
        val likeCountCommand = commands.first
        val viewCountCommand = commands.second

        if (likeCountCommand.likeCountGroupBy.isNotEmpty()) {
            productMetricService.updateLikeCount(likeCountCommand.likeCountGroupBy)
        }
        if (viewCountCommand.viewCountGroupBy.isNotEmpty()) {
            productMetricService.updateViewCount(viewCountCommand.viewCountGroupBy)
        }
    }

    private fun buildCommands(
        messages: List<ConsumerRecord<String, String>>,
    ): Pair<LikeCountUpdateCommand, ViewCountUpdateCommand> {
        val eventIds = mutableSetOf<String>()
        val likeCountGroupBy = mutableMapOf<Long, Long>()
        val viewCountGroupBy = mutableMapOf<Long, Long>()

        messages.forEach { record ->
            val payload = objectMapper.readValue(record.value(), CatalogEventPayload::class.java)
            val eventId = payload.eventId

            if (eventIds.contains(eventId)) {
                return@forEach
            }

            if (eventHandleService.duplicatedBy(eventId)) {
                return@forEach
            }

            when (payload.type) {
                CatalogType.LIKED -> {
                    likeCountGroupBy[payload.productId] =
                        likeCountGroupBy.getOrPut(payload.productId) { 0L } + 1L
                }

                CatalogType.UNLIKED -> {
                    likeCountGroupBy[payload.productId] =
                        likeCountGroupBy.getOrPut(payload.productId) { 0L } - 1L
                }

                CatalogType.DETAIL_VIEW -> {
                    viewCountGroupBy[payload.productId] =
                        viewCountGroupBy.getOrPut(payload.productId) { 0L } + 1L
                }
            }

            eventIds.add(eventId)
            eventHandleService.ensureCatalogEvent(eventId)
        }

        return Pair(
            LikeCountUpdateCommand(likeCountGroupBy = likeCountGroupBy),
            ViewCountUpdateCommand(viewCountGroupBy = viewCountGroupBy),
        )
    }
}
