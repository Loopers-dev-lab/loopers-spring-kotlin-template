package com.loopers.application.order

import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.event.PaymentCompletedEvent
import com.loopers.domain.payment.event.PaymentFailedEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderEventHandler(
    private val orderService: OrderService,
    @Qualifier("eventCoroutineScope")
    private val coroutineScope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // 이 핸들러는 동기로 실행되어야 하므로 코루틴 불필요
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handlePaymentCompleted(event: PaymentCompletedEvent) {
        try {
            logger.info("결제 완료 처리 시작: orderId=${event.orderId}")
            orderService.completeOrderWithPayment(event.orderId)
            logger.info("결제 완료 처리 완료: orderId=${event.orderId}")
        } catch (e: Exception) {
            logger.error("결제 완료 처리 실패: orderId=${event.orderId}, error=${e.message}", e)
            throw e
        }
    }

    // 비동기 로깅만 하므로 코루틴으로 전환
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePaymentFailed(event: PaymentFailedEvent) {
        coroutineScope.launch {
            logger.info("결제 실패 처리: orderId=${event.orderId}")
        }
    }
}
