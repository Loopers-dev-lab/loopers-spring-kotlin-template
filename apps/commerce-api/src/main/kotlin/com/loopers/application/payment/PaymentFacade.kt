package com.loopers.application.payment

import com.loopers.application.order.PaymentResultHandler
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PgClient
import com.loopers.infrastructure.pg.PgException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.ZonedDateTime

/**
 * 결제 관련 비즈니스 로직을 오케스트레이션하는 Facade
 *
 * - IN_PROGRESS 결제 조회
 * - PG 상태 확인 후 성공/실패 처리
 * - 보상 트랜잭션 관리
 */
@Component
class PaymentFacade(
    private val paymentService: PaymentService,
    private val orderService: OrderService,
    private val paymentResultHandler: PaymentResultHandler,
    private val pgClient: PgClient,
    private val transactionTemplate: TransactionTemplate,
) {
    private val logger = LoggerFactory.getLogger(PaymentFacade::class.java)

    /**
     * IN_PROGRESS 상태의 결제 목록을 조회합니다.
     *
     * @param threshold 조회 기준 시간 (이전에 업데이트된 결제만 조회)
     * @return IN_PROGRESS 상태의 Payment 목록
     */
    fun findInProgressPayments(threshold: ZonedDateTime): List<Payment> {
        return paymentService.findInProgressPayments(threshold)
    }

    /**
     * IN_PROGRESS 상태의 결제를 처리합니다.
     * PG 트랜잭션을 조회하고 Payment.confirmPayment()로 상태를 결정합니다.
     *
     * @param payment 처리할 결제
     */
    fun processInProgressPayment(payment: Payment) {
        // PG 트랜잭션 조회 (트랜잭션 외부)
        val transactions = try {
            pgClient.findTransactionsByOrderId(payment.orderId)
        } catch (e: PgException) {
            logger.warn("PG 상태 조회 실패 - paymentId: {}, reason: {}", payment.id, e.message)
            return // 다음 스케줄에 재시도
        }

        val orderItems = getOrderItems(payment.orderId)
        val currentTime = Instant.now()

        // 결제 결과 처리 (트랜잭션 내부)
        transactionTemplate.execute { _ ->
            paymentResultHandler.handlePaymentResult(
                paymentId = payment.id,
                transactions = transactions,
                currentTime = currentTime,
                orderItems = orderItems,
            )
        }
    }

    /**
     * 주문 상품 정보를 조회합니다 (재고 복구용).
     */
    private fun getOrderItems(orderId: Long): List<PaymentResultHandler.OrderItemInfo> {
        val order = orderService.findById(orderId)
        return order.orderItems.map {
            PaymentResultHandler.OrderItemInfo(
                productId = it.productId,
                quantity = it.quantity,
            )
        }
    }
}
