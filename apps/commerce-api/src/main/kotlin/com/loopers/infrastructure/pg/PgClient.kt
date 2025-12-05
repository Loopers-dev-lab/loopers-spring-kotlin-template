package com.loopers.infrastructure.pg

/**
 * PG(Payment Gateway) 클라이언트 인터페이스
 */
interface PgClient {
    /**
     * 결제 요청
     * @return 트랜잭션 키와 상태
     * @throws PgException PG 호출 실패 시
     */
    fun requestPayment(
        userId: Long,
        request: PgPaymentRequest,
    ): PgPaymentResponse

    /**
     * 트랜잭션 키로 결제 상태 조회
     */
    fun getPaymentByKey(
        userId: Long,
        transactionKey: String,
    ): PgPaymentDetailResponse

    /**
     * 주문 ID로 결제 목록 조회
     */
    fun getPaymentsByOrderId(
        userId: Long,
        orderId: String,
    ): PgPaymentListResponse
}
