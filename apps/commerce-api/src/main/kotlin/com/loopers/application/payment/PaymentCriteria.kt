package com.loopers.application.payment

/**
 * 결제 관련 Facade 파라미터 클래스
 */
class PaymentCriteria {
    /**
     * PG 콜백 처리 요청 파라미터
     *
     * @param orderId 주문 ID
     * @param externalPaymentKey PG 외부 결제 키
     */
    data class ProcessCallback(
        val orderId: Long,
        val externalPaymentKey: String,
    )
}
