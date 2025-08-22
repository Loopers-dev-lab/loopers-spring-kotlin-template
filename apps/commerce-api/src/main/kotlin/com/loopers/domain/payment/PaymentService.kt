package com.loopers.domain.payment

import com.loopers.domain.payment.entity.Payment
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class PaymentService(
    private val paymentRepository: PaymentRepository,
) {
    fun get(id: Long): Payment {
        return paymentRepository.find(id)
            ?: throw CoreException(errorType = ErrorType.NOT_FOUND, customMessage = "[id = $id] 결제를 찾을 수 없습니다.")
    }

    fun get(transactionKey: String): Payment {
        return paymentRepository.find(transactionKey)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[transactionKey = $transactionKey] 결제를 찾을 수 없습니다.",
            )
    }

    fun request(payment: Payment): Payment {
        return paymentRepository.save(payment)
    }
}
