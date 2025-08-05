package com.loopers.domain.payment.dto.result

import com.loopers.domain.payment.entity.Payment
import com.loopers.domain.payment.entity.Payment.Method
import com.loopers.domain.payment.entity.Payment.Status
import java.math.BigDecimal
import java.time.ZonedDateTime

class PaymentResult {
    data class PaymentDetail(
        val id: Long,
        val orderId: Long,
        val paymentMethod: Method,
        val paymentPrice: BigDecimal,
        val status: Status,
        val createAt: ZonedDateTime,
        val updateAt: ZonedDateTime,
    ) {
        companion object {
            fun from(payment: Payment): PaymentDetail {
                return PaymentDetail(
                    payment.id,
                    payment.orderId,
                    payment.paymentMethod,
                    payment.paymentPrice.value,
                    payment.status,
                    payment.createdAt,
                    payment.updatedAt,
                )
            }
        }
    }

    data class PaymentDetails(
        val payments: List<PaymentDetail>,
    ) {
        companion object {
            fun from(payments: Payment): PaymentDetails {
                return PaymentDetails(
                    listOf(PaymentDetail.from(payments)),
                )
            }

            fun from(payments: List<Payment>): PaymentDetails {
                return PaymentDetails(
                    payments.map { PaymentDetail.from(it) },
                )
            }
        }
    }
}
