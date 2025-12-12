package com.loopers.interfaces.event.point

import com.loopers.domain.payment.PaymentFailedEventV1
import com.loopers.domain.point.PointService
import com.loopers.support.values.Money
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class PointEventListener(
    private val pointService: PointService,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun onPaymentFailed(event: PaymentFailedEventV1) {
        if (event.usedPoint > Money.ZERO_KRW) {
            pointService.restore(event.userId, event.usedPoint)
        }
    }
}
