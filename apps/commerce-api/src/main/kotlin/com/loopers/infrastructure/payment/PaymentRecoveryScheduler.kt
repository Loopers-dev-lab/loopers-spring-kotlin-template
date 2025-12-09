package com.loopers.infrastructure.payment

import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.strategy.PgStrategy
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class PaymentRecoveryScheduler(
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val orderService: OrderService,
    private val pgStrategies: List<PgStrategy>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    @Transactional
    fun recoverPendingPayments() {
        val cutoffTime = ZonedDateTime.now().minusMinutes(10)

        val staleOrders = orderRepository.findByStatusAndCreatedAtBefore(
            OrderStatus.PENDING,
            cutoffTime
        )

        if (staleOrders.isEmpty()) return

        logger.info("복구 대상 주문 발견: ${staleOrders.size}건")

        staleOrders.forEach { order ->
            val payment = paymentRepository.findByOrderId(order.id!!)
                .firstOrNull { it.status == PaymentStatus.PENDING }
            
            if (payment == null) {
                logger.warn("PENDING Payment not found for orderId=${order.id}")
                return@forEach
            }

            if (payment.transactionKey == null) {
                logger.warn("TransactionKey is null for orderId=${order.id}, paymentId=${payment.id} - Cannot recover (Fallback case)")
                return@forEach
            }

            val pgStrategy = pgStrategies.firstOrNull { it.supports(payment.paymentMethod) }
            if (pgStrategy == null) {
                logger.warn("No PG strategy found for paymentMethod=${payment.paymentMethod}")
                return@forEach
            }

            try {
                val pgStatus = pgStrategy.getPaymentStatus(order.memberId, payment.transactionKey!!)
                logger.info("PG 상태 조회: orderId=${order.id}, status=${pgStatus.status}")

                if (pgStatus.status == "SUCCESS") {
                    // Order 도메인이 재고 차감 포함한 완료 처리를 담당
                    orderService.completeOrderWithPayment(order.id!!)
                    payment.markAsSuccess()
                    logger.info("결제 복구 완료: orderId=${order.id}, transactionKey=${payment.transactionKey}")
                } else {
                    logger.info("PG에서 여전히 대기 중: orderId=${order.id}, status=${pgStatus.status}")
                }
            } catch (e: Exception) {
                logger.error("결제 복구 실패: orderId=${order.id}", e)
            }
        }
    }
}
