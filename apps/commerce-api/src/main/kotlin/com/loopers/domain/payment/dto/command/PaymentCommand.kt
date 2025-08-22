package com.loopers.domain.payment.dto.command

import com.loopers.domain.payment.entity.Payment
import com.loopers.domain.payment.entity.Payment.Method
import com.loopers.domain.payment.type.CardType
import com.loopers.domain.payment.type.TransactionStatus
import java.math.BigDecimal

class PaymentCommand {
    data class Request(
        val orderId: Long,
        val paymentMethod: Method,
        val cardType: String,
        val cardNumber: String,
    ) {
        fun toEntity(paymentPrice: BigDecimal): Payment {
            var payment = Payment.create(orderId, paymentMethod, paymentPrice, Payment.Status.REQUESTED, cardType, cardNumber)
            if (paymentMethod == Method.POINT) {
                payment = Payment.create(orderId, paymentMethod, paymentPrice, Payment.Status.REQUESTED)
            }
            return payment
        }

        companion object {
            fun of(orderId: Long, paymentMethod: Method, cardType: String, cardNumber: String): Request {
                return Request(orderId, paymentMethod, cardType, cardNumber)
            }
        }
    }

    data class PaymentWebhook(
        val transactionKey: String,
        val orderId: String,
        val cardType: CardType,
        val cardNo: String,
        val amount: Long,
        val status: TransactionStatus,
        val reason: String?,
    ) {
        companion object {
            fun of(
                transactionKey: String,
                   orderId: String,
                   cardType: CardType,
                   cardNo: String,
                   amount: Long,
                   status: TransactionStatus,
                   reason: String?,
            ): PaymentWebhook {
                return PaymentWebhook(
                    transactionKey,
                    orderId,
                    cardType,
                    cardNo,
                    amount,
                    status,
                    reason,
                    )
            }
        }
    }
}
