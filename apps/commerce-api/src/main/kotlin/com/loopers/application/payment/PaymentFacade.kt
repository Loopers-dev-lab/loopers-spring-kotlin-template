package com.loopers.application.payment

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductCommand
import com.loopers.domain.product.ProductService
import com.loopers.infrastructure.payment.PgRequestNotReachedException
import com.loopers.support.values.Money
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.retry.support.RetryTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * 결제 관련 비즈니스 로직을 오케스트레이션하는 Facade
 *
 * - IN_PROGRESS 결제 조회
 * - PG 콜백 처리 후 후속 처리
 * - 보상 트랜잭션 관리
 */
@Component
class PaymentFacade(
    private val paymentService: PaymentService,
    private val orderService: OrderService,
    private val pointService: PointService,
    private val couponService: CouponService,
    private val productService: ProductService,
    private val transactionTemplate: TransactionTemplate,
    private val retryTemplate: RetryTemplate = RetryTemplate.builder()
        .maxAttempts(2)
        .uniformRandomBackoff(100, 500)
        .retryOn(ObjectOptimisticLockingFailureException::class.java)
        .build(),
) {
    /**
     * IN_PROGRESS 상태의 결제 목록을 조회합니다.
     *
     * @return IN_PROGRESS 상태의 Payment 목록
     */
    fun findInProgressPayments(): List<Payment> {
        return paymentService.findInProgressPayments()
    }

    /**
     * PG 콜백을 처리합니다.
     * PaymentService에 처리를 위임하고, 결과에 따라 후속 처리를 수행합니다.
     *
     * @param criteria 콜백 처리 파라미터 (orderId, externalPaymentKey)
     */
    fun processCallback(criteria: PaymentCriteria.ProcessCallback) {
        val result = paymentService.processCallback(
            orderId = criteria.orderId,
            externalPaymentKey = criteria.externalPaymentKey,
        )

        when (result) {
            is PaymentService.CallbackResult.Confirmed -> {
                handleConfirmedPayment(result.payment)
            }

            else -> {
                return
            }
        }
    }

    /**
     * 확정된 결제에 대한 후속 처리를 수행합니다.
     */
    private fun handleConfirmedPayment(payment: Payment) {
        when (payment.status) {
            PaymentStatus.PAID -> {
                orderService.completePayment(payment.orderId)
            }

            PaymentStatus.FAILED -> {
                retryTemplate.execute<Unit, Exception> {
                    recoverResources(payment)
                }
            }

            else -> {}
        }
    }

    /**
     * IN_PROGRESS 상태의 결제를 처리합니다.
     * 스케줄러에서 호출됩니다.
     *
     * @param paymentId 처리할 결제 ID
     * @throws PgRequestNotReachedException PG 연결 실패 시
     */
    fun processInProgressPayment(paymentId: Long) {
        val result = paymentService.processInProgressPayment(paymentId)

        when (result) {
            is PaymentService.CallbackResult.Confirmed -> {
                handleConfirmedPayment(result.payment)
            }

            else -> {
                return
            }
        }
    }

    /**
     * 결제 실패 시 리소스를 복구합니다.
     * - 포인트 복구
     * - 쿠폰 사용 취소
     * - 재고 복구
     */
    private fun recoverResources(payment: Payment) {
        transactionTemplate.execute { _ ->
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
            orderService.cancelOrder(payment.orderId)
        }
    }
}
