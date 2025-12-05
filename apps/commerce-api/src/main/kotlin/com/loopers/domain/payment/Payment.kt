package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import com.loopers.domain.order.Order
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.Version

@Table(name = "payments")
@Entity
class Payment(
    orderId: Long,
    userId: Long,
    totalAmount: Money,
    usedPoint: Money,
    paidAmount: Money,
    status: PaymentStatus,
    issuedCouponId: Long? = null,
    couponDiscount: Money = Money.ZERO_KRW,
    externalPaymentKey: String? = null,
    failureMessage: String? = null,
) : BaseEntity() {
    var orderId: Long = orderId
        private set

    var userId: Long = userId
        private set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "total_amount", nullable = false))
    var totalAmount: Money = totalAmount
        private set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "used_point", nullable = false))
    var usedPoint: Money = usedPoint
        private set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "paid_amount", nullable = false))
    var paidAmount: Money = paidAmount
        private set

    @Column(name = "issued_coupon_id")
    var issuedCouponId: Long? = issuedCouponId
        private set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "coupon_discount", nullable = false))
    var couponDiscount: Money = couponDiscount
        private set

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: PaymentStatus = status
        private set

    @Column(name = "external_payment_key", length = 100)
    var externalPaymentKey: String? = externalPaymentKey
        private set

    @Column(name = "failure_message", length = 500)
    var failureMessage: String? = failureMessage
        private set

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        private set

    /**
     * 결제를 시작합니다. PENDING → IN_PROGRESS 상태 전이
     * @throws CoreException PENDING 상태가 아닌 경우
     */
    fun start() {
        if (status != PaymentStatus.PENDING) {
            throw CoreException(ErrorType.BAD_REQUEST, "결제 대기 상태에서만 결제를 시작할 수 있습니다")
        }
        status = PaymentStatus.IN_PROGRESS
    }

    /**
     * PG 트랜잭션 키를 저장합니다.
     * @throws CoreException IN_PROGRESS 상태가 아닌 경우
     */
    fun updateExternalPaymentKey(key: String) {
        if (status != PaymentStatus.IN_PROGRESS) {
            throw CoreException(ErrorType.BAD_REQUEST, "결제 진행 중 상태에서만 외부 결제 키를 저장할 수 있습니다")
        }
        externalPaymentKey = key
    }

    /**
     * 결제 IN_PROGRESS → PAID 상태 전이
     * @throws CoreException IN_PROGRESS 상태가 아닌 경우
     */
    fun paid() {
        if (status != PaymentStatus.IN_PROGRESS) {
            throw CoreException(ErrorType.BAD_REQUEST, "결제 진행 중 상태에서만 성공 처리할 수 있습니다")
        }
        status = PaymentStatus.PAID
    }

    /**
     * 결제를 실패 처리합니다. PENDING/IN_PROGRESS → FAILED 상태 전이
     * @param message 실패 사유 (nullable)
     * @throws CoreException 이미 처리된 결제(PAID/FAILED)인 경우
     */
    fun fail(message: String?) {
        if (status == PaymentStatus.PAID || status == PaymentStatus.FAILED) {
            throw CoreException(ErrorType.BAD_REQUEST, "이미 처리된 결제입니다")
        }
        status = PaymentStatus.FAILED
        failureMessage = message
    }

    companion object {
        /**
         * 결제를 생성합니다.
         * paidAmount = totalAmount - usedPoint - couponDiscount 로 자동 계산
         * - paidAmount가 0이면 (포인트+쿠폰으로 전액 결제) → PAID
         * - paidAmount가 0보다 크면 (PG 결제 필요) → PENDING
         */
        fun create(
            userId: Long,
            order: Order,
            usedPoint: Money,
            issuedCouponId: Long? = null,
            couponDiscount: Money = Money.ZERO_KRW,
        ): Payment {
            if (usedPoint < Money.ZERO_KRW) {
                throw CoreException(ErrorType.BAD_REQUEST, "사용 포인트는 0 이상이어야 합니다")
            }

            val paidAmount = order.totalAmount - usedPoint - couponDiscount

            if (paidAmount < Money.ZERO_KRW) {
                throw CoreException(ErrorType.BAD_REQUEST, "포인트와 쿠폰 할인의 합이 주문 금액을 초과합니다")
            }

            val status = if (paidAmount == Money.ZERO_KRW) PaymentStatus.PAID else PaymentStatus.PENDING

            return Payment(
                orderId = order.id,
                userId = userId,
                totalAmount = order.totalAmount,
                usedPoint = usedPoint,
                paidAmount = paidAmount,
                status = status,
                issuedCouponId = issuedCouponId,
                couponDiscount = couponDiscount,
            )
        }

        fun of(
            orderId: Long,
            userId: Long,
            totalAmount: Money,
            usedPoint: Money,
            status: PaymentStatus,
            issuedCouponId: Long? = null,
            couponDiscount: Money = Money.ZERO_KRW,
            paidAmount: Money = Money.ZERO_KRW,
            externalPaymentKey: String? = null,
            failureMessage: String? = null,
        ): Payment {
            return Payment(
                orderId = orderId,
                userId = userId,
                totalAmount = totalAmount,
                usedPoint = usedPoint,
                paidAmount = paidAmount,
                status = status,
                issuedCouponId = issuedCouponId,
                couponDiscount = couponDiscount,
                externalPaymentKey = externalPaymentKey,
                failureMessage = failureMessage,
            )
        }
    }
}
