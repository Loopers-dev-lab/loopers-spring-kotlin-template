package com.loopers.infrastructure.pg

import com.loopers.interfaces.api.ApiResponse

object PgDto {

    data class PaymentRequest(
        val orderId: String,
        val cardType: CardTypeDto,
        val cardNo: String,
        val amount: Long,
        val callbackUrl: String
    )

    data class PaymentResponse(
        val transactionKey: String,
        val status: TransactionStatusDto,
        val reason: String?
    )

    data class PaymentStatusResponse(
        val transactionKey: String,
        val orderId: String,
        val cardType: CardTypeDto,
        val cardNo: String,
        val amount: Long,
        val status: String,
        val reason: String?
    )

    data class OrderPaymentsResponse(
        val orderId: String,
        val transactions: List<TransactionResponse>
    )

    data class TransactionResponse(
        val transactionKey: String,
        val status: String,
        val reason: String?
    )

    enum class CardTypeDto {
        SAMSUNG,
        KB,
        HYUNDAI
    }

    enum class TransactionStatusDto {
        PENDING,
        SUCCESS,
        FAILED
    }
}
