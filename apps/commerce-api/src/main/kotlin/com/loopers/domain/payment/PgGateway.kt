package com.loopers.domain.payment

import com.loopers.domain.payment.dto.PgCommand
import com.loopers.domain.payment.dto.PgInfo

/**
 * PG 시스템과의 통신을 담당하는 Gateway 인터페이스
 */
interface PgGateway {

    /**
     * 결제 요청
     * @param command 결제 정보
     * @return 거래 정보
     */
    fun requestPayment(
        command: PgCommand.Request,
    ): PgInfo.Transaction

    /**
     * 거래 정보 조회
     * @param userId 사용자 ID
     * @param transactionKey 거래 키
     * @return 거래 정보 (없으면 null)
     */
    fun getPayment(
        userId: String,
        transactionKey: String,
    ): PgInfo.Transaction?

    /**
     * 주문별 결제 정보 조회
     * @param userId 사용자 ID
     * @param orderId 주문 ID
     * @return 주문별 결제 정보 (없으면 null)
     */
    fun getPaymentByOrderId(
        userId: String,
        orderId: String,
    ): PgInfo.Order?
}
