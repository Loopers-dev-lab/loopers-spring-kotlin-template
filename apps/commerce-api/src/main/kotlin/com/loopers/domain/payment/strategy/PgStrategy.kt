package com.loopers.domain.payment.strategy

import com.loopers.domain.payment.PaymentMethod
import com.loopers.infrastructure.pg.PgDto

interface PgStrategy {
    fun supports(paymentMethod: PaymentMethod): Boolean
    fun requestPayment(
        userId: String,
        request: PgDto.PaymentRequest
    ): PgDto.PaymentResponse
    fun getPaymentStatus(
        userId: String,
        transactionKey: String
    ): PgDto.PaymentStatusResponse
}
