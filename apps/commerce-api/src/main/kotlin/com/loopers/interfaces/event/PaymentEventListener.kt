package com.loopers.interfaces.event

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.PaymentCreatedEventV1
import com.loopers.domain.payment.PaymentFailedEventV1
import com.loopers.domain.payment.PaymentPaidEventV1
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductCommand
import com.loopers.domain.product.ProductService
import com.loopers.support.values.Money
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class PaymentEventListener(
    private val paymentService: PaymentService,
    private val orderService: OrderService,
    private val pointService: PointService,
    private val couponService: CouponService,
    private val productService: ProductService,
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPaymentCreated(event: PaymentCreatedEventV1) {
        paymentService.requestPgPayment(event.paymentId)
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPaymentPaid(event: PaymentPaidEventV1) {
        orderService.completePayment(event.orderId)
    }

    /**
     * 결제 실패 시 리소스 복구 처리
     *
     * 주의: Spring의 BEFORE_COMMIT 리스너에서 발행된 이벤트의 BEFORE_COMMIT 리스너는
     * 호출되지 않을 수 있으므로, 재고 복구는 OrderCanceledEventV1 체인을 거치지 않고
     * 직접 처리합니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun onPaymentFailed(event: PaymentFailedEventV1) {
        // 1. 포인트 복원
        if (event.usedPoint > Money.ZERO_KRW) {
            pointService.restore(event.userId, event.usedPoint)
        }

        // 2. 쿠폰 복원
        event.issuedCouponId?.let {
            couponService.cancelCouponUse(it)
        }

        // 3. 주문 취소 및 재고 복구
        // cancelOrder()가 발행하는 OrderCanceledEventV1의 BEFORE_COMMIT 리스너가
        // 중첩된 BEFORE_COMMIT 컨텍스트에서 호출되지 않으므로 직접 재고 복구
        val cancelledOrder = orderService.cancelOrder(event.orderId)
        val increaseUnits = cancelledOrder.orderItems.map {
            ProductCommand.IncreaseStockUnit(productId = it.productId, amount = it.quantity)
        }
        productService.increaseStocks(ProductCommand.IncreaseStocks(units = increaseUnits))
    }
}
