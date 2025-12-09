package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

data class PaymentCallbackDto(
    val transactionKey: String,
    val status: String,
    val reason: String?
) {
    init {
        if (transactionKey.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "거래 키는 필수입니다")
        }
        if (transactionKey.length > 100) {
            throw CoreException(ErrorType.BAD_REQUEST, "거래 키는 100자를 초과할 수 없습니다")
        }
        if (status.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "결제 상태는 필수입니다")
        }
    }

    fun isSuccess(): Boolean = status == "SUCCESS"
}
