package com.loopers.domain.pg

interface PgClient {
    fun requestPayment(request: PgPaymentRequest): PgPaymentCreateResult
    fun findTransaction(transactionKey: String): PgTransaction
    fun findTransactionsByOrderId(orderId: Long): List<PgTransaction>
}
