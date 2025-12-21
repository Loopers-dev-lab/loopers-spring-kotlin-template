package com.loopers.interfaces.handler.order

import com.loopers.domain.order.OrderSuccessEvent
import com.loopers.domain.outbox.OutBoxEventType
import com.loopers.domain.outbox.OutBoxService
import com.loopers.event.EventType
import com.loopers.event.OrderEventPayload
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderEventHandler(private val outBoxService: OutBoxService) {

    companion object {
        private val logger = LoggerFactory.getLogger(OrderEventHandler::class.java)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleOrderedBefore(event: OrderSuccessEvent) {
        val payload =
            OrderEventPayload(
                eventId = event.eventId,
                orderId = event.orderId,
                orderItems = event.orderItems,
            )

        outBoxService.enqueue(
            event.eventId,
            EventType.Topic.ORDER_EVENT,
            payload,
            OutBoxEventType.ORDER,
        )
    }
}
