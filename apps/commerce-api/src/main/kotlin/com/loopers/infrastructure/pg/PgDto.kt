package com.loopers.infrastructure.pg

import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

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
        HYUNDAI;

        companion object {
            fun from(cardType: String): CardTypeDto {
                return try {
                    valueOf(cardType.uppercase())
                } catch (e: IllegalArgumentException) {
                    throw CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 카드 타입입니다: $cardType")
                }
            }
        }
    }

    enum class TransactionStatusDto {
        PENDING,
        SUCCESS,
        FAILED
    }
}
