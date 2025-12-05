package com.loopers.application.order

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.Payment
import com.loopers.domain.order.PaymentService
import com.loopers.domain.order.PaymentStatus
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductCommand
import com.loopers.domain.product.ProductService
import com.loopers.support.values.Money
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

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
     * 결제 성공 처리
     * - Payment 상태를 PAID로 변경
     * - Order 상태를 PAID로 변경
     *
     * @param paymentId 결제 ID
     * @param externalPaymentKey PG사 거래 키
     */
    @Transactional
    fun handlePaymentSuccess(paymentId: Long, externalPaymentKey: String? = null) {
        logger.info("결제 성공 처리 시작 - paymentId: {}", paymentId)

        val payment = paymentService.completePayment(paymentId, externalPaymentKey)
        orderService.pay(
            com.loopers.domain.order.OrderCommand.Pay(
                orderId = payment.orderId,
                userId = payment.userId,
                usePoint = payment.usedPoint,
                issuedCouponId = payment.issuedCouponId,
                couponDiscount = payment.couponDiscount,
            ),
        )

        logger.info("결제 성공 처리 완료 - paymentId: {}, orderId: {}", paymentId, payment.orderId)
    }

    /**
     * 결제 실패 처리 및 리소스 복구
     * - Payment 상태를 FAILED로 변경
     * - 차감했던 포인트 복구
     * - 사용했던 쿠폰 복구
     * - 감소했던 재고 복구
     * - Order 상태를 CANCELLED로 변경
     *
     * @param paymentId 결제 ID
     * @param reason 실패 사유
     * @param orderItems 주문 상품 목록 (재고 복구용)
     */
    @Transactional
    fun handlePaymentFailure(
        paymentId: Long,
        reason: String?,
        orderItems: List<OrderItemInfo>? = null,
    ) {
        logger.info("결제 실패 처리 시작 - paymentId: {}, reason: {}", paymentId, reason)

        val payment = paymentService.findById(paymentId)

        // 이미 FAILED 상태면 중복 처리 방지
        if (payment.status == PaymentStatus.FAILED) {
            logger.info("이미 실패 처리된 결제 - paymentId: {}", paymentId)
            return
        }

        // 결제 실패 처리
        paymentService.failPayment(paymentId, reason)

        // 리소스 복구
        recoverResources(payment, orderItems)

        // 주문 취소
        orderService.cancelOrder(payment.orderId)

        logger.info("결제 실패 처리 완료 - paymentId: {}, orderId: {}", paymentId, payment.orderId)
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
