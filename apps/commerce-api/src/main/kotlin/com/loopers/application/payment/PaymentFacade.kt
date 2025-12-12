package com.loopers.application.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentService
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Component

/**
 * 결제 관련 비즈니스 로직을 오케스트레이션하는 Facade
 *
 * - IN_PROGRESS 결제 조회
 * - PG 콜백 처리 후 후속 처리
 *
 * 결제 성공/실패에 따른 리소스 처리는 PaymentService가 발행하는 이벤트를 통해
 * PaymentEventListener에서 처리됩니다:
 * - PaymentPaidEventV1 -> orderService.completePayment()
 * - PaymentFailedEventV1 -> pointService.restore(), couponService.cancelCouponUse(),
 *                          orderService.cancelOrder(), productService.increaseStocks()
 */
@Component
class PaymentFacade(
    private val paymentService: PaymentService,
) {
    /**
     * 결제 목록을 조회합니다.
     * pagination과 동적 조건(status)을 지원합니다.
     *
     * @param criteria 조회 조건 (page, size, sort, statuses)
     * @return Slice<Payment> 결제 목록
     */
    fun findPayments(criteria: PaymentCriteria.FindPayments): Slice<Payment> {
        return paymentService.findPayments(criteria.toCommand())
    }

    /**
     * PG 콜백을 처리합니다.
     * PaymentService에 처리를 위임합니다.
     * 결과에 따른 후속 처리(주문 완료, 리소스 복구)는 이벤트 리스너에서 처리됩니다.
     *
     * @param criteria 콜백 처리 파라미터 (orderId, externalPaymentKey)
     */
    fun processCallback(criteria: PaymentCriteria.ProcessCallback) {
        paymentService.processCallback(
            orderId = criteria.orderId,
            externalPaymentKey = criteria.externalPaymentKey,
        )
    }

    /**
     * IN_PROGRESS 상태의 결제를 처리합니다.
     * 스케줄러에서 호출됩니다.
     * 결과에 따른 후속 처리(주문 완료, 리소스 복구)는 이벤트 리스너에서 처리됩니다.
     *
     * @param paymentId 처리할 결제 ID
     */
    fun processInProgressPayment(paymentId: Long) {
        paymentService.processInProgressPayment(paymentId)
    }
}
