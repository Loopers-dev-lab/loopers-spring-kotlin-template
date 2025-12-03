package com.loopers.domain.payment

import com.loopers.domain.payment.dto.PaymentCommand
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@Transactional
class PaymentService(
    private val paymentRepository: PaymentRepository,
) {

    fun getBy(orderId: Long): Payment? {
        return paymentRepository.findBy(orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Payment not found for orderId: $orderId")
    }

    fun create(command: PaymentCommand.Create): Payment {
        return paymentRepository.save(Payment.create(command))
    }

    fun updateTransactionKey(orderId: Long, transactionKey: String) {
        val payment = paymentRepository.findBy(orderId)
            ?: throw IllegalArgumentException("Payment not found for orderId: $orderId")
        payment.updateTransactionKey(transactionKey)
    }

    fun findPending(beforeDateTime: LocalDateTime): List<Payment> {
        return paymentRepository.findPending(PaymentStatus.PENDING, beforeDateTime)
    }

    fun approvePayment(payment: Payment, transactionKey: String) {
        payment.updateTransactionKey(transactionKey)
        payment.success()
    }

    fun success(orderId: Long) {
        val payment = getBy(orderId)
        payment?.success()
    }

    fun fail(orderId: Long, reason: String?) {
        val payment = getBy(orderId)
        payment?.fail(reason)
    }
}
