package com.loopers.domain.pg

sealed class PgPaymentCreateResult {
    data class Accepted(val transactionKey: String) : PgPaymentCreateResult()
    data object Uncertain : PgPaymentCreateResult()
}
