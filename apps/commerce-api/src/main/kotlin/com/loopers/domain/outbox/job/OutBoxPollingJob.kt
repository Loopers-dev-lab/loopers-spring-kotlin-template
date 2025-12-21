package com.loopers.domain.outbox.job

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.domain.outbox.OutBoxEventType
import com.loopers.domain.outbox.OutBoxRepository
import com.loopers.domain.outbox.OutboxStatus
import org.apache.kafka.common.KafkaException
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OutBoxPollingJob(
    private val outBoxRepository: OutBoxRepository,
    private val outBoxService: com.loopers.domain.outbox.OutBoxService,
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val objectMapper: ObjectMapper,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(OutBoxPollingJob::class.java)
    }

    @Scheduled(fixedDelay = 60000)
    fun pollAndPublishOutBox() {
        val pendingOutBoxes =
            outBoxRepository.findAllByStatusIn(
                listOf(OutboxStatus.PENDING, OutboxStatus.FAILED),
            )

        if (pendingOutBoxes.isEmpty()) {
            logger.debug("No pending or failed outbox messages to publish")
            return
        }

        logger.info("Found ${pendingOutBoxes.size} pending/failed outbox messages to publish")

        pendingOutBoxes.forEach { outbox ->
            try {
                val payloadMap: Map<String, Any> = objectMapper.readValue(outbox.payload)

                // Order 이벤트인 경우 orderId를 파티션 키로 사용
                val partitionKey =
                    when (outbox.eventType) {
                        OutBoxEventType.ORDER -> {
                            val orderId = payloadMap["orderId"] as? Number
                            orderId?.toString()
                        }

                        OutBoxEventType.CATALOG, null -> null
                    }

                if (partitionKey != null) {
                    kafkaTemplate.send(outbox.topic, partitionKey, payloadMap)
                } else {
                    kafkaTemplate.send(outbox.topic, payloadMap)
                }

                outBoxService.markAsPublished(outbox.eventId)
            } catch (e: KafkaException) {
                logger.error(
                    "Failed to publish outbox message: eventId=${outbox.eventId}, topic=${outbox.topic}",
                    e,
                )
                outBoxService.markAsFailed(outbox.eventId)
            } catch (e: Exception) {
                logger.error(
                    "Unexpected error while publishing outbox message: eventId=${outbox.eventId}, topic=${outbox.topic}",
                    e,
                )
                outBoxService.markAsFailed(outbox.eventId)
            }
        }
    }
}
