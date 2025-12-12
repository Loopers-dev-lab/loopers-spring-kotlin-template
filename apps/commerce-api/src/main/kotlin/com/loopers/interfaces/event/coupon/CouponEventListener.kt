package com.loopers.interfaces.event.coupon

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.payment.PaymentFailedEventV1
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class CouponEventListener(
    private val couponService: CouponService,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun onPaymentFailed(event: PaymentFailedEventV1) {
        event.issuedCouponId?.let {
            couponService.cancelCouponUse(it)
        }
    }
}
