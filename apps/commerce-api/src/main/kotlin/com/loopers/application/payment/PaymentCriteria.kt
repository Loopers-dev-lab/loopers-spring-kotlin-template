package com.loopers.application.payment

import com.loopers.domain.payment.PaymentCommand
import com.loopers.domain.payment.PaymentSortType
import com.loopers.domain.payment.PaymentStatus

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

    /**
     * 결제 목록 조회 파라미터
     *
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기
     * @param sort 정렬 타입
     * @param statuses 조회할 결제 상태 목록 (빈 리스트면 전체 조회)
     */
    data class FindPayments(
        val page: Int? = null,
        val size: Int? = null,
        val sort: PaymentSortType? = null,
        val statuses: List<PaymentStatus> = emptyList(),
    ) {
        fun toCommand(): PaymentCommand.FindPayments {
            return PaymentCommand.FindPayments(
                page = page,
                size = size,
                sort = sort,
                statuses = statuses,
            )
        }
    }
}
