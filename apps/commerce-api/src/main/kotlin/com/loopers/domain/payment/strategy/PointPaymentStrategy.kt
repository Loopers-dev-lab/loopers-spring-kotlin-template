package com.loopers.domain.payment.strategy

import com.loopers.domain.order.entity.Order
import com.loopers.domain.payment.entity.Payment
import com.loopers.domain.point.PointService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PointPaymentStrategy(
    private val pointService: PointService,
) : PaymentStrategy {

    override fun supports() = Payment.Method.POINT

    @Transactional
    override fun process(order: Order, payment: Payment) {
        val point = pointService.get(order.userId)
        point.use(payment.paymentPrice.value)

        payment.success()
        order.success()
    }
}
