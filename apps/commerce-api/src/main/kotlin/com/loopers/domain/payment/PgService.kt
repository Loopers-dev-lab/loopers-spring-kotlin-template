package com.loopers.domain.payment

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class PgService(
    private val pgClient: PgClient,
    @Value("\${pg.callback-url}")
    private val pgCallbackUrl: String,
) {

    fun requestPayment(userId: String, request: PgDto.PgRequest): String {
        val response = pgClient.requestPayment(
            userId = userId,
            request = PgDto.PgRequest(
                orderId = request.orderId,
                cardType = request.cardType,
                cardNo = request.cardNo,
                amount = request.amount,
                callbackUrl = pgCallbackUrl,
            ),
        )
        return response.data!!.transactionKey
    }

    fun getPayment(userId: String, transactionKey: String) {
        pgClient.getPayment(
            userId = userId,
            transactionKey = transactionKey,
        )
    }

    fun getPaymentByOrderId(userId: String, orderId: String): PgDto.PgOrderResponse? {
        val response = pgClient.getPaymentByOrderId(
            userId = userId,
            orderId = orderId,
        )
        return response.data!!
    }
}
