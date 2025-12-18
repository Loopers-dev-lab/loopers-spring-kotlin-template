package com.loopers.infrastructure.integration

import com.loopers.domain.outbox.Outbox
import com.loopers.domain.outbox.OutboxEvent
import com.loopers.domain.outbox.OutboxEventPublisher
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Outbox 이벤트를 Kafka로 발행하는 Publisher
 */
@Component
class OutboxEventPublisherImpl(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
) : OutboxEventPublisher {

    private val log = LoggerFactory.getLogger(OutboxEventPublisherImpl::class.java)

    companion object {
        private const val SEND_TIMEOUT_SECONDS = 10L
        private const val EVENT_ID_HEADER = "eventId"

        /**
         * 이벤트 타입별 토픽 매핑
         */
        private val EVENT_TOPIC_MAP = mapOf(
            OutboxEvent.LikeCountChanged.EVENT_TYPE to OutboxEvent.LikeCountChanged.TOPIC,
            OutboxEvent.ViewCountIncreased.EVENT_TYPE to OutboxEvent.ViewCountIncreased.TOPIC,
            OutboxEvent.OrderCompleted.EVENT_TYPE to OutboxEvent.OrderCompleted.TOPIC,
            OutboxEvent.OrderCanceled.EVENT_TYPE to OutboxEvent.OrderCanceled.TOPIC,
            OutboxEvent.SoldOut.EVENT_TYPE to OutboxEvent.SoldOut.TOPIC,
        )
    }

    /**
     * Outbox 이벤트를 Kafka로 발행
     *
     * @param outbox 발행할 Outbox 엔티티
     * @return 발행 성공 여부
     */
    @Retry(name = "outboxPublisher")
    override fun publish(outbox: Outbox): Boolean {
        val topic = resolveTopic(outbox)

        return try {
            val message = MessageBuilder
                .withPayload(outbox.payload)
                .setHeader(KafkaHeaders.TOPIC, topic)
                .setHeader(KafkaHeaders.KEY, outbox.aggregateId)
                .setHeader(EVENT_ID_HEADER, outbox.eventId)
                .build()

            val future = kafkaTemplate.send(message)

            // 동기적으로 발행 결과 확인
            future.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            log.debug(
                "Outbox 이벤트 발행 성공: id={}, eventId={}, topic={}, aggregateType={}, eventType={}",
                outbox.id,
                outbox.eventId,
                topic,
                outbox.aggregateType,
                outbox.eventType,
            )
            true
        } catch (e: Exception) {
            log.error(
                "Outbox 이벤트 발행 실패: id={}, eventId={}, topic={}, aggregateType={}, eventType={}",
                outbox.id,
                outbox.eventId,
                topic,
                outbox.aggregateType,
                outbox.eventType,
                e,
            )
            false
        }
    }

    /**
     * Outbox 이벤트에 맞는 토픽 결정
     */
    private fun resolveTopic(outbox: Outbox): String {
        return EVENT_TOPIC_MAP[outbox.eventType]
            ?: throw IllegalArgumentException("No topic mapping for eventType: ${outbox.eventType}")
    }
}
