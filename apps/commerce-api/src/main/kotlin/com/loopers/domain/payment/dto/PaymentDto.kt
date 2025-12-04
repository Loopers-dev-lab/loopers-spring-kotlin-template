package com.loopers.domain.payment.dto

import com.loopers.domain.payment.PaymentModel
import java.math.BigDecimal

sealed class PaymentDto {

    data class Request(
        val orderId: String,
        val cardType: String,
        val cardNo: String,
        val amount: BigDecimal,
        val callbackUrl: String,
    ) {
        companion object {
            fun from(orderKey: String, cardType: String, cardNo: String, amount: BigDecimal) = Request(
                orderKey,
                cardType = cardType,
                cardNo = cardNo,
                amount = amount,
                callbackUrl = "http://localhost:8080/payment/callback",
            )
        }
    }

    data class Response(val transactionKey: String, val status: String)

    // PaymentService 실행 결과
    sealed class Result {
        data class Success(val payment: PaymentModel) : Result()
        data class Failed(val errorMessage: String) : Result()
    }
}
