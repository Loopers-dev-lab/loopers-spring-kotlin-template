package com.loopers.interfaces.api.payment

import com.loopers.domain.payment.dto.command.PaymentCommand
import com.loopers.domain.payment.dto.result.PaymentResult
import com.loopers.domain.payment.entity.Payment.Method
import com.loopers.domain.payment.entity.Payment.Status
import java.math.BigDecimal
import java.time.ZonedDateTime

class PaymentV1Dto {
    data class PaymentResponse(
        val id: Long,
        val orderId: Long,
        val paymentMethod: Method,
        val paymentPrice: BigDecimal,
        val status: Status,
        val createAt: ZonedDateTime,
        val updateAt: ZonedDateTime,
    ) {
        companion object {
            fun from(paymentDetail: PaymentResult.PaymentDetail?): PaymentResponse? {
                return paymentDetail
                    ?.let {
                        PaymentResponse(
                        it.id,
                        it.orderId,
                        it.paymentMethod,
                        it.paymentPrice,
                        it.status,
                        it.createAt,
                        it.updateAt,
                    )
                    }
            }
        }
    }

    data class PaymentRequest(
        val orderId: Long,
        val paymentMethod: Method,
    ) {
        fun toCommand(): PaymentCommand.Request {
            return PaymentCommand.Request.of(orderId, paymentMethod)
        }
    }
}
