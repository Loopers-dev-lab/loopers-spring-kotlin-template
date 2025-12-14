package com.loopers.infrastructure.event

import com.loopers.domain.order.event.OrderCreatedEvent
import com.loopers.domain.payment.event.PaymentCompletedEvent
import com.loopers.infrastructure.dataplatform.DataPlatformClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class DataPlatformEventHandler(
    private val dataPlatformClient: DataPlatformClient,
    @Qualifier("eventCoroutineScope")
    private val coroutineScope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderCreated(event: OrderCreatedEvent) {
        coroutineScope.launch {
            try {
                logger.info("주문 데이터 플랫폼 전송 시작: orderId=${event.orderId}")
                dataPlatformClient.sendOrderData(
                    orderId = event.orderId,
                    memberId = event.memberId,
                    amount = event.orderAmount
                )
                logger.info("주문 데이터 플랫폼 전송 완료: orderId=${event.orderId}")
            } catch (e: Exception) {
                logger.error("주문 데이터 플랫폼 전송 실패: orderId=${event.orderId}, error=${e.message}", e)
                // 실패해도 주문에는 영향 없음
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePaymentCompleted(event: PaymentCompletedEvent) {
        coroutineScope.launch {
            try {
                logger.info("결제 데이터 플랫폼 전송 시작: paymentId=${event.paymentId}")
                dataPlatformClient.sendPaymentData(
                    paymentId = event.paymentId,
                    orderId = event.orderId,
                    memberId = event.memberId,
                    amount = event.amount
                )
                logger.info("결제 데이터 플랫폼 전송 완료: paymentId=${event.paymentId}")
            } catch (e: Exception) {
                logger.error("결제 데이터 플랫폼 전송 실패: paymentId=${event.paymentId}, error=${e.message}", e)
            }
        }

    }
}
