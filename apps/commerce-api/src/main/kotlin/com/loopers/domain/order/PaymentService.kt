package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
) {
    /**
     * PENDING 상태의 결제를 생성합니다.
     * PG 결제를 위한 초기 상태로, 리소스(포인트, 쿠폰, 재고)가 할당된 후 호출됩니다.
     *
     * @param userId 사용자 ID
     * @param order 주문 엔티티
     * @param usedPoint 사용할 포인트
     * @param paidAmount 카드로 결제할 금액
     * @param issuedCouponId 사용할 쿠폰 ID (nullable)
     * @param couponDiscount 쿠폰 할인 금액
     * @return 생성된 Payment
     */
    @Transactional
    fun createPending(
        userId: Long,
        order: Order,
        usedPoint: Money,
        paidAmount: Money,
        issuedCouponId: Long? = null,
        couponDiscount: Money = Money.ZERO_KRW,
    ): Payment {
        val payment = Payment.pending(
            userId = userId,
            order = order,
            usedPoint = usedPoint,
            paidAmount = paidAmount,
            issuedCouponId = issuedCouponId,
            couponDiscount = couponDiscount,
        )

        return paymentRepository.save(payment)
    }

    /**
     * 결제를 시작합니다. PENDING → IN_PROGRESS 상태 전이.
     * PG API 호출 직전에 호출됩니다.
     *
     * @param paymentId 결제 ID
     * @return 상태가 변경된 Payment
     * @throws CoreException 결제를 찾을 수 없는 경우
     */
    @Transactional
    fun startPayment(paymentId: Long): Payment {
        val payment = paymentRepository.findById(paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다")

        payment.start()

        return paymentRepository.save(payment)
    }

    /**
     * 결제를 완료합니다. IN_PROGRESS → PAID 상태 전이.
     * PG 결제 성공 또는 콜백 수신 시 호출됩니다.
     *
     * @param paymentId 결제 ID
     * @param externalPaymentKey PG사 거래 키 (nullable)
     * @return 상태가 변경된 Payment
     * @throws CoreException 결제를 찾을 수 없는 경우
     */
    @Transactional
    fun completePayment(paymentId: Long, externalPaymentKey: String? = null): Payment {
        val payment = paymentRepository.findById(paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다")

        externalPaymentKey?.let { payment.updateExternalPaymentKey(it) }
        payment.success()

        return paymentRepository.save(payment)
    }

    /**
     * 결제를 실패 처리합니다. PENDING/IN_PROGRESS → FAILED 상태 전이.
     * PG 결제 실패 또는 복구 처리 시 호출됩니다.
     *
     * @param paymentId 결제 ID
     * @param message 실패 메시지 (nullable)
     * @return 상태가 변경된 Payment
     * @throws CoreException 결제를 찾을 수 없는 경우
     */
    @Transactional
    fun failPayment(paymentId: Long, message: String? = null): Payment {
        val payment = paymentRepository.findById(paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다")

        payment.fail(message)

        return paymentRepository.save(payment)
    }

    /**
     * 스케줄러에서 사용할 IN_PROGRESS 상태의 결제 목록을 조회합니다.
     * 지정된 임계값보다 오래된 IN_PROGRESS 결제를 조회하여 복구 처리합니다.
     *
     * @param threshold 조회 기준 시간 (이전에 업데이트된 결제만 조회)
     * @return IN_PROGRESS 상태의 Payment 목록
     */
    @Transactional(readOnly = true)
    fun findInProgressPayments(threshold: ZonedDateTime): List<Payment> {
        return paymentRepository.findByStatusInAndUpdatedAtBefore(
            statuses = listOf(PaymentStatus.IN_PROGRESS),
            before = threshold,
        )
    }

    /**
     * 외부 결제 키로 결제를 조회합니다.
     * PG 콜백에서 거래를 식별할 때 사용됩니다.
     *
     * @param key PG사 거래 키
     * @return Payment (nullable)
     */
    @Transactional(readOnly = true)
    fun findByExternalPaymentKey(key: String): Payment? {
        return paymentRepository.findByExternalPaymentKey(key)
    }

    /**
     * 결제 ID로 결제를 조회합니다.
     *
     * @param paymentId 결제 ID
     * @return Payment
     * @throws CoreException 결제를 찾을 수 없는 경우
     */
    @Transactional(readOnly = true)
    fun findById(paymentId: Long): Payment {
        return paymentRepository.findById(paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다")
    }
}
