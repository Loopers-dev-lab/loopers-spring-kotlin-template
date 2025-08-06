package com.loopers.domain.payment.dto.command

import com.loopers.domain.payment.entity.Payment
import com.loopers.domain.payment.entity.Payment.Method
import java.math.BigDecimal

class PaymentCommand {
    data class Request(
        val orderId: Long,
        val paymentMethod: Method,
    ) {
        fun toEntity(paymentPrice: BigDecimal): Payment {
            return Payment.create(orderId, paymentMethod, paymentPrice, Payment.Status.REQUESTED)
        }
    }

    data class Process(
        val orderId: Long,
        val paymentId: Long,
    )
}
