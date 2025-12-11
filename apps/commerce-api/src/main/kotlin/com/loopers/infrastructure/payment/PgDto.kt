package com.loopers.infrastructure.payment

data class PgPaymentRequest(
    val orderId: String,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
    val callbackUrl: String,
)

data class PgResponse<T>(
    val meta: PgMeta,
    val data: T?,
)

data class PgMeta(
    val result: String,
    val errorCode: String?,
    val message: String?,
)

data class PgPaymentResponse(
    val transactionKey: String,
    val status: String,
    val reason: String?,
)

data class PgPaymentDetailResponse(
    val transactionKey: String,
    val orderId: String,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
    val status: String,
    val reason: String?,
)

data class PgPaymentListResponse(
    val orderId: String,
    val transactions: List<PgTransactionSummary>,
)

data class PgTransactionSummary(
    val transactionKey: String,
    val status: String,
    val reason: String?,
)
