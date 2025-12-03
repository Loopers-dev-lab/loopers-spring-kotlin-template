package com.loopers.domain.payment

object PgDto {
    data class PgRequest(
        val orderId: String,
        val cardType: CardType,
        val cardNo: String,
        val amount: Long,
        val callbackUrl: String? = null,
    )

    data class PgOrderResponse(
        val orderId: String,
        val transactions: List<PgTransactionResponse>,
    )

    data class PgTransactionResponse(
        val transactionKey: String,
        val status: String?,
    )

    data class PgTransactionDetailResponse(
        val transactionKey: String,
        val orderId: String,
        val cardType: CardType,
        val cardNo: String,
        val amount: Long,
        val status: PaymentStatus,
        val reason: String?,
    )
}
