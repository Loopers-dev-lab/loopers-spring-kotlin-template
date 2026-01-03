package com.loopers.infrastructure.dataplatform.client

import com.loopers.domain.event.OrderCreatedEvent
import com.loopers.domain.event.PaymentCompletedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 데이터 플랫폼 클라이언트 구현체
 * 외부 시스템이 완성되기 전까지 로깅으로 대체
 * TODO: 실제 데이터 플랫폼 API 호출 구현
 */
@Component
class DataPlatformClientImpl : DataPlatformClient {
    companion object {
        private val logger = LoggerFactory.getLogger(DataPlatformClientImpl::class.java)
    }

    override fun sendOrderCreated(event: OrderCreatedEvent) {
        logger.debug(
            "데이터 플랫폼 전송 (Mock): orderId=${event.orderId}, userId=${event.userId}, amount=${event.amount}",
        )
    }

    override fun sendPaymentCompleted(event: PaymentCompletedEvent) {
        logger.debug(
            "결제 완료 데이터 플랫폼 전송 (Mock): orderId=${event.orderId}, paymentId=${event.paymentId}",
        )
    }
}
