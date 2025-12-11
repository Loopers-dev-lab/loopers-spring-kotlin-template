package com.loopers.domain.payment

data class PgTransaction(
    val transactionKey: String,
    val paymentId: Long,
    val status: PgTransactionStatus,
    val failureReason: String? = null,
)
