package com.loopers.domain.payment

sealed class PgPaymentResult {
    data class Success(val payment: Payment) : PgPaymentResult()
    data class Failed(val payment: Payment, val reason: String) : PgPaymentResult()
}
