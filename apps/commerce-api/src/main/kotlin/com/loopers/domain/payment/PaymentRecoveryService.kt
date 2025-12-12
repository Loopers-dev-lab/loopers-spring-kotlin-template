package com.loopers.domain.payment

import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.strategy.PgStrategy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class PaymentRecoveryService(
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val transactionService: PaymentRecoveryTransactionService,
    private val pgStrategies: List<PgStrategy>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun recoverPendingPayments() {
        val cutoffTime = ZonedDateTime.now().minusMinutes(10)

        val staleOrders = orderRepository.findByStatusAndCreatedAtBefore(
            OrderStatus.PENDING,
            cutoffTime
        )

        if (staleOrders.isEmpty()) return

        log.info("복구 대상 주문 발견: ${staleOrders.size}건")

        staleOrders.forEach { order ->
            try {
                processStaleOrder(order)
            } catch (e: Exception) {
                log.error("주문 복구 실패: orderId=${order.id}", e)
            }
        }
    }

    private fun processStaleOrder(order: com.loopers.domain.order.Order) {
        val payment = paymentRepository.findByOrderId(order.id!!)
            .firstOrNull { it.status == PaymentStatus.PENDING }

        if (payment == null) {
            log.warn("PENDING Payment not found for orderId=${order.id}")
            return
        }

        if (payment.transactionKey == null) {
            log.warn("TransactionKey is null for orderId=${order.id}, paymentId=${payment.id}")
            return
        }

        val pgStrategy = pgStrategies.firstOrNull { it.supports(payment.paymentMethod) }
        if (pgStrategy == null) {
            log.warn("No PG strategy found for paymentMethod=${payment.paymentMethod}")
            return
        }

        // PG 조회 (트랜잭션 외부에서 실행)
        val pgStatus = pgStrategy.getPaymentStatus(order.memberId, payment.transactionKey!!)
        log.info("PG 상태 조회: orderId=${order.id}, status=${pgStatus.status}")

        // 3. PG 상태에 따라 별도 서비스의 트랜잭션 메서드 호출 (프록시를 거침)
        when (pgStatus.status) {
            "SUCCESS" -> transactionService.handlePaymentSuccess(order.id!!, payment)
            "FAILED", "CANCELED" -> transactionService.handlePaymentFailure(order.id!!, payment, pgStatus.reason)
            else -> log.info("PG에서 여전히 대기 중: orderId=${order.id}, status=${pgStatus.status}")
        }
    }
}
