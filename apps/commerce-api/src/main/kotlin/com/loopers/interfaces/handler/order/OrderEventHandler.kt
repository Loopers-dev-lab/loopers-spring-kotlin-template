package com.loopers.interfaces.handler.order

import com.loopers.domain.order.OrderSuccessEvent
import com.loopers.domain.outbox.OutBoxService
import com.loopers.event.EventType
import com.loopers.event.OrderEventPayload
import com.loopers.interfaces.handler.like.LikeEventHandler
import org.apache.kafka.common.KafkaException
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderEventHandler(private val outBoxService: OutBoxService, private val kafkaTemplate: KafkaTemplate<Any, Any>) {

    companion object {
        private val logger = LoggerFactory.getLogger(LikeEventHandler::class.java)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleOrderedBefore(event: OrderSuccessEvent) {
        val payload = OrderEventPayload(
            eventId = event.eventId,
            orderId = event.orderId,
            orderItems = event.orderItems,
        )

        outBoxService.enqueue(event.eventId, EventType.Topic.ORDER_EVENT, payload)
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderSuccess(event: OrderSuccessEvent) {
        val payload = OrderEventPayload(
            eventId = event.eventId,
            orderId = event.orderId,
            orderItems = event.orderItems,
        )

        try {
            kafkaTemplate.send(EventType.Topic.ORDER_EVENT, payload)
        } catch (e: KafkaException) {
            logger.error("Error sending like event", e)
            outBoxService.markAsFailed(event.eventId)
        }
        outBoxService.markAsPublished(event.eventId)
    }
}
