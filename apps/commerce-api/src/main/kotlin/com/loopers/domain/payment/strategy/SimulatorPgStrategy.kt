package com.loopers.domain.payment.strategy

import com.loopers.domain.payment.PaymentMethod
import com.loopers.infrastructure.pg.PgDto
import com.loopers.infrastructure.pg.PgSimulatorClient
import org.springframework.stereotype.Component

@Component
class SimulatorPgStrategy(
    private val pgSimulatorClient: PgSimulatorClient
) : PgStrategy {

    override fun supports(paymentMethod: PaymentMethod): Boolean {
        return paymentMethod == PaymentMethod.CARD
    }

    override fun requestPayment(
        userId: String,
        request: PgDto.PaymentRequest
    ): PgDto.PaymentResponse {
        return pgSimulatorClient.requestPayment(userId, request)
    }

    override fun getPaymentStatus(
        userId: String,
        transactionKey: String
    ): PgDto.PaymentStatusResponse {
        return pgSimulatorClient.getPaymentStatus(userId, transactionKey)
    }
}
