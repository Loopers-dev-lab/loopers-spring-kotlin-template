package com.loopers.domain.payment

import com.loopers.domain.payment.dto.PgCommand
import com.loopers.domain.payment.dto.PgInfo
import io.github.resilience4j.retry.annotation.Retry
import org.springframework.stereotype.Service

@Service
class PgService(
    private val pgGateway: PgGateway,
) {

    @Retry(name = "pgClient")
    fun requestPayment(command: PgCommand.Request): String {
        val transaction = pgGateway.requestPayment(command)
        return transaction.transactionKey
    }

    @Retry(name = "pgClient")
    fun getPayment(userId: String, transactionKey: String): PgInfo.Transaction? {
        return pgGateway.getPayment(
            userId = userId,
            transactionKey = transactionKey,
        )
    }

    @Retry(name = "pgClient")
    fun getPaymentByOrderId(userId: String, orderId: String): PgInfo.Order? {
        return pgGateway.getPaymentByOrderId(
            userId = userId,
            orderId = orderId,
        )
    }
}
