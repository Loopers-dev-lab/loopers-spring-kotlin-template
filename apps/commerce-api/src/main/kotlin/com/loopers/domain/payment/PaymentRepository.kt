package com.loopers.domain.payment

interface PaymentRepository {
    fun save(payment: Payment): Payment
    fun findById(paymentId: Long): Payment?
    fun findByTransactionKey(transactionKey: String): Payment?
    fun findByOrderId(orderId: Long): List<Payment>
    fun findByIdOrThrow(paymentId: Long): Payment
}
