package com.loopers.domain.payment

interface PgClient {
    fun requestPayment(request: PgPaymentRequest): PgPaymentCreateResult
    fun findTransaction(transactionKey: String): PgTransaction
    fun findTransactionsByPaymentId(paymentId: Long): List<PgTransaction>
}
