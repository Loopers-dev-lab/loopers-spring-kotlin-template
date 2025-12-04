package com.loopers.domain.payment

interface PaymentRepository {
    fun save(payment: PaymentModel): PaymentModel
    fun findByTransactionKey(transactionKey: String): PaymentModel?
}
