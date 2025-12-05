package com.loopers.infrastructure.payment

import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.strategy.PgStrategy
import com.loopers.domain.product.ProductService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class PaymentRecoveryScheduler(
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val productService: ProductService,
    private val pgStrategies: List<PgStrategy>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 60000)
    @Transactional
    fun recoverPendingPayments() {
        val cutoffTime = LocalDateTime.now().minusMinutes(10)

        val staleOrders = orderRepository.findByStatusAndCreatedAtBefore(
            OrderStatus.PENDING,
            cutoffTime
        )

        staleOrders.forEach { order ->
            val payment = paymentRepository.findByOrderId(order.id!!)
                .firstOrNull { it.status == PaymentStatus.PENDING } ?: return@forEach

            if (payment.transactionKey == null) return@forEach

            val pgStrategy = pgStrategies.firstOrNull { it.supports(payment.paymentMethod) } ?: return@forEach
            val pgStatus = pgStrategy.getPaymentStatus(order.memberId, payment.transactionKey!!)

            if (pgStatus.status == "SUCCESS") {
                productService.decreaseStockByOrder(order)
                payment.markAsSuccess()
                order.complete()
                logger.info("결제 복구 완료: orderId=${order.id}")
            }
        }
    }
}
