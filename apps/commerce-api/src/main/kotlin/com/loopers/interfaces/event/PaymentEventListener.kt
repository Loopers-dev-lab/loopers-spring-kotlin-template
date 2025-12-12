package com.loopers.interfaces.event

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.PaymentCreatedEventV1
import com.loopers.domain.payment.PaymentFailedEventV1
import com.loopers.domain.payment.PaymentPaidEventV1
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.point.PointService
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

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun onPaymentFailed(event: PaymentFailedEventV1) {
        if (event.usedPoint > Money.ZERO_KRW) {
            pointService.restore(event.userId, event.usedPoint)
        }

        event.issuedCouponId?.let {
            couponService.cancelCouponUse(it)
        }

        orderService.cancelOrder(event.orderId)
    }
}
