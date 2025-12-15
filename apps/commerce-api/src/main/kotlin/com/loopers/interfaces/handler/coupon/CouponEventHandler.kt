package com.loopers.interfaces.handler.coupon

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderSuccessEvent
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class CouponEventHandler(private val couponService: CouponService) {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderSuccess(event: OrderSuccessEvent) = event.couponId?.let { couponService.useCoupon(it, event.userId) }
}
