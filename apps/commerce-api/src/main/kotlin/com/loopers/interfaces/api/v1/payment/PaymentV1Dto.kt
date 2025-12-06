package com.loopers.interfaces.api.v1.payment

import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.dto.PaymentCommand

class PaymentV1Dto {
    data class CallbackRequest(
        val transactionKey: String,
        val orderId: String,
        val status: PaymentStatus,
        val reason: String?,
    ) {
        fun toCommand(): PaymentCommand.Callback {
            return PaymentCommand.Callback(
                transactionKey = transactionKey,
                orderId = orderId,
                status = status,
                reason = reason,
            )
        }
    }
}
