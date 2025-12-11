package com.loopers.domain.payment

sealed class PgPaymentCreateResult {
    data class Accepted(val transactionKey: String) : PgPaymentCreateResult()
    data object Uncertain : PgPaymentCreateResult()
    data object NotReached : PgPaymentCreateResult()
    data object NotRequired : PgPaymentCreateResult()
}
