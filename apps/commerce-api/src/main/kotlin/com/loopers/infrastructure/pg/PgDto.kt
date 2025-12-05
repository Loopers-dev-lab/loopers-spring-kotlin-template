package com.loopers.infrastructure.pg

/**
 * PG 결제 요청 DTO
 */
data class PgPaymentRequest(
    val orderId: String,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
    val callbackUrl: String,
)

/**
 * PG 공통 응답 래퍼
 */
data class PgResponse<T>(
    val meta: PgMeta,
    val data: T?,
)

data class PgMeta(
    val result: String,
    val errorCode: String?,
    val message: String?,
) {
    fun isSuccess(): Boolean = result == "SUCCESS"
}

/**
 * 결제 요청 응답
 */
data class PgPaymentResponse(
    val transactionKey: String,
    val status: String,
    val reason: String?,
)

/**
 * 결제 상세 조회 응답
 */
data class PgPaymentDetailResponse(
    val transactionKey: String,
    val orderId: String,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
    val status: String,
    val reason: String?,
)

/**
 * 주문별 결제 목록 조회 응답
 */
data class PgPaymentListResponse(
    val orderId: String,
    val transactions: List<PgTransactionSummary>,
)

data class PgTransactionSummary(
    val transactionKey: String,
    val status: String,
    val reason: String?,
)

/**
 * PG 트랜잭션 상태
 */
enum class PgTransactionStatus {
    PENDING,
    SUCCESS,
    FAILED,
}

/**
 * 카드 타입
 */
enum class CardType {
    SAMSUNG,
    KB,
    HYUNDAI,
}
