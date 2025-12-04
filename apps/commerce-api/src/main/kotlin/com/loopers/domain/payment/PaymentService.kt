package com.loopers.domain.payment

import com.loopers.domain.payment.dto.PaymentCommand
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZonedDateTime

@Service
@Transactional
class PaymentService(
    private val paymentRepository: PaymentRepository,
) {

    fun getBy(orderId: Long): Payment {
        return paymentRepository.findBy(orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Payment not found for orderId: $orderId")
    }

    fun getBy(transactionKey: String): Payment {
        return paymentRepository.findBy(transactionKey)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Payment not found for transactionKey: $transactionKey")
    }

    fun create(command: PaymentCommand.Create): Payment {
        return paymentRepository.save(Payment.create(command))
    }

    fun updateTransactionKey(orderId: Long, transactionKey: String) {
        val payment = paymentRepository.findBy(orderId)
            ?: throw IllegalArgumentException("Payment not found for orderId: $orderId")
        payment.updateTransactionKey(transactionKey)
    }

    fun findPending(beforeDateTime: ZonedDateTime): List<Payment> {
        return paymentRepository.findPending(PaymentStatus.PENDING, beforeDateTime)
    }

    fun approvePayment(payment: Payment, transactionKey: String) {
        payment.updateTransactionKey(transactionKey)
        payment.success()
    }

    fun success(transactionKey: Long) {
        val payment = getBy(transactionKey)
        payment.success()
    }

    fun fail(transactionKey: Long, reason: String?) {
        val payment = getBy(transactionKey)
        payment.fail(reason)
    }
}
