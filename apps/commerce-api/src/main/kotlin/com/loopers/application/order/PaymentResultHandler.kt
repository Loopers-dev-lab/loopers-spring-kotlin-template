package com.loopers.application.order

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductCommand
import com.loopers.domain.product.ProductService
import com.loopers.support.values.Money
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 결제 결과 처리를 담당하는 핸들러
 * Webhook 콜백 및 스케줄러에서 호출됩니다.
 */
@Component
class PaymentResultHandler(
    private val paymentService: PaymentService,
    private val orderService: OrderService,
    private val pointService: PointService,
    private val couponService: CouponService,
    private val productService: ProductService,
) {
    private val logger = LoggerFactory.getLogger(PaymentResultHandler::class.java)

    /**
     * PG 트랜잭션 결과로 결제 상태를 확정하고 후속 처리를 수행합니다.
     *
     * - PAID: Order 상태를 PAID로 변경
     * - FAILED: 리소스 복구 (포인트, 쿠폰, 재고) 및 Order 취소
     * - IN_PROGRESS: 상태 유지 (아무 작업 안 함)
     *
     * @param paymentId 결제 ID
     * @param transactions PG에서 조회한 트랜잭션 목록
     * @param currentTime 현재 시각 (타임아웃 판단용)
     * @param orderItems 주문 상품 목록 (재고 복구용, nullable)
     */
    @Transactional
    fun handlePaymentResult(
        paymentId: Long,
        transactions: List<PgTransaction>,
        currentTime: Instant,
        orderItems: List<OrderItemInfo>? = null,
    ) {
        logger.info("결제 결과 처리 시작 - paymentId: {}", paymentId)

        val payment = paymentService.confirmPayment(paymentId, transactions, currentTime)

        when (payment.status) {
            PaymentStatus.PAID -> {
                orderService.completePayment(payment.orderId)
                logger.info("결제 성공 처리 완료 - paymentId: {}, orderId: {}", paymentId, payment.orderId)
            }
            PaymentStatus.FAILED -> {
                recoverResources(payment, orderItems)
                orderService.cancelOrder(payment.orderId)
                logger.info("결제 실패 처리 완료 - paymentId: {}, orderId: {}", paymentId, payment.orderId)
            }
            PaymentStatus.IN_PROGRESS -> {
                logger.info("결제 상태 유지 - paymentId: {}", paymentId)
            }
            else -> {
                logger.warn("예상치 못한 결제 상태 - paymentId: {}, status: {}", paymentId, payment.status)
            }
        }
    }

    /**
     * 리소스 복구 (포인트, 쿠폰, 재고)
     */
    private fun recoverResources(payment: Payment, orderItems: List<OrderItemInfo>?) {
        // 1. 포인트 복구
        if (payment.usedPoint > Money.ZERO_KRW) {
            pointService.restore(payment.userId, payment.usedPoint)
            logger.debug("포인트 복구 완료 - userId: {}, amount: {}", payment.userId, payment.usedPoint)
        }

        // 2. 쿠폰 복구
        payment.issuedCouponId?.let { couponId ->
            couponService.cancelCouponUse(couponId)
            logger.debug("쿠폰 복구 완료 - issuedCouponId: {}", couponId)
        }

        // 3. 재고 복구
        orderItems?.let { items ->
            val increaseUnits = items.map { item ->
                ProductCommand.IncreaseStockUnit(
                    productId = item.productId,
                    amount = item.quantity,
                )
            }
            productService.increaseStocks(ProductCommand.IncreaseStocks(units = increaseUnits))
            logger.debug("재고 복구 완료 - items: {}", items.size)
        }
    }

    /**
     * 주문 상품 정보 (재고 복구용)
     */
    data class OrderItemInfo(
        val productId: Long,
        val quantity: Int,
    )
}
