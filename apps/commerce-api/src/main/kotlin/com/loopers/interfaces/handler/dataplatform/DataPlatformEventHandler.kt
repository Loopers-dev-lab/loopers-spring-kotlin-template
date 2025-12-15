package com.loopers.interfaces.handler.dataplatform

import com.loopers.domain.order.OrderSuccessEvent
import com.loopers.infrastructure.dataplatform.DataPlatformClient
import com.loopers.infrastructure.dataplatform.OrderEventPayload
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class DataPlatformEventHandler(private val dataPlatformClient: DataPlatformClient) {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderSuccess(event: OrderSuccessEvent) {
        dataPlatformClient.sendOrderEvent(OrderEventPayload.from(event))
    }
}
