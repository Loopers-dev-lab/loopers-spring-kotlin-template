package com.loopers.application.payment

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgClient
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductCommand
import com.loopers.domain.product.ProductService
import com.loopers.support.values.Money
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
    private val pointService: PointService,
    private val couponService: CouponService,
    private val productService: ProductService,
    private val pgClient: PgClient,
    private val transactionTemplate: TransactionTemplate,
) {
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
     * @param paymentId 처리할 결제 ID
     * @throws PgRequestNotReachedException PG 연결 실패 시
     */
    fun processInProgressPayment(paymentId: Long) {
        val payment = paymentService.findById(paymentId)

        // PG 트랜잭션 조회 (트랜잭션 외부)
        val transactions = pgClient.findTransactionsByOrderId(payment.orderId)
        val currentTime = Instant.now()

        // 결제 결과 처리 (트랜잭션 내부)
        transactionTemplate.execute { _ ->
            val confirmedPayment = paymentService.confirmPayment(payment.id, transactions, currentTime)

            if (confirmedPayment.status == PaymentStatus.PAID) {
                orderService.completePayment(confirmedPayment.orderId)
                return@execute
            }

            // 실패 처리
            if (confirmedPayment.status == PaymentStatus.FAILED) {
                if (payment.usedPoint > Money.ZERO_KRW) {
                    pointService.restore(payment.userId, payment.usedPoint)
                }
                payment.issuedCouponId?.let { couponId ->
                    couponService.cancelCouponUse(couponId)
                }
                val order = orderService.findById(payment.orderId)
                val increaseUnits = order.orderItems.map {
                    ProductCommand.IncreaseStockUnit(productId = it.productId, amount = it.quantity)
                }
                productService.increaseStocks(ProductCommand.IncreaseStocks(units = increaseUnits))

                orderService.cancelOrder(confirmedPayment.orderId)
            }
        }
    }
}
