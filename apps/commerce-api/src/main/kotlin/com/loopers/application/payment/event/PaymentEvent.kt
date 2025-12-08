package com.loopers.application.payment.event

import com.loopers.domain.payment.CardType

object PaymentEvent {
    /**
     * 결제가 생성되고 PG 요청이 필요할 때 발생하는 이벤트
     */
    data class PaymentCreated(
        val paymentId: Long,
        val orderId: String,
        val userId: String,
        val cardType: CardType,
        val cardNo: String,
        val amount: Long,
    )

    /**
     * 결제 성공 시 발생하는 이벤트
     */
    data class PaymentSucceeded(
        val paymentId: Long,
        val orderId: Long,
        val userId: Long,
        val totalAmount: Long,
    )

    /**
     * 결제 실패 시 발생하는 이벤트
     */
    data class PaymentFailed(
        val paymentId: Long,
        val orderId: Long,
        val userId: Long,
        val couponId: Long?,
        val reason: String?,
    )
}
