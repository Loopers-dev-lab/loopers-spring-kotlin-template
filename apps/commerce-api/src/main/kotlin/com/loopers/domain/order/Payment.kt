package com.loopers.domain.order

import com.loopers.domain.BaseEntity
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
     * 결제를 성공 처리합니다. IN_PROGRESS → PAID 상태 전이
     * @throws CoreException IN_PROGRESS 상태가 아닌 경우
     */
    fun success() {
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
         * 포인트 전액 결제용 팩토리 메서드 (기존 방식 - 즉시 PAID)
         */
        fun pay(
            userId: Long,
            order: Order,
            usedPoint: Money,
            issuedCouponId: Long? = null,
            couponDiscount: Money = Money.ZERO_KRW,
        ): Payment {
            if (usedPoint < Money.ZERO_KRW) {
                throw CoreException(ErrorType.BAD_REQUEST, "사용 포인트는 0 이상이어야 합니다.")
            }

            // 주문 금액에서 쿠폰 할인을 뺀 금액이 포인트로 결제되어야 함
            val expectedPayment = order.totalAmount - couponDiscount

            if (usedPoint != expectedPayment) {
                throw CoreException(ErrorType.BAD_REQUEST, "사용 포인트가 결제 금액과 일치하지 않습니다.")
            }

            return Payment(
                orderId = order.id,
                userId = userId,
                totalAmount = order.totalAmount,
                usedPoint = usedPoint,
                paidAmount = Money.ZERO_KRW,
                status = PaymentStatus.PAID,
                issuedCouponId = issuedCouponId,
                couponDiscount = couponDiscount,
            )
        }

        /**
         * PG 결제를 위한 PENDING 상태 결제 생성
         * usedPoint + paidAmount + couponDiscount == totalAmount 검증
         */
        fun pending(
            userId: Long,
            order: Order,
            usedPoint: Money,
            paidAmount: Money,
            issuedCouponId: Long? = null,
            couponDiscount: Money = Money.ZERO_KRW,
        ): Payment {
            if (usedPoint < Money.ZERO_KRW) {
                throw CoreException(ErrorType.BAD_REQUEST, "사용 포인트는 0 이상이어야 합니다")
            }

            if (paidAmount < Money.ZERO_KRW) {
                throw CoreException(ErrorType.BAD_REQUEST, "카드 결제 금액은 0 이상이어야 합니다")
            }

            val totalPayment = usedPoint + paidAmount + couponDiscount
            if (totalPayment != order.totalAmount) {
                throw CoreException(ErrorType.BAD_REQUEST, "결제 금액이 주문 금액과 일치하지 않습니다")
            }

            return Payment(
                orderId = order.id,
                userId = userId,
                totalAmount = order.totalAmount,
                usedPoint = usedPoint,
                paidAmount = paidAmount,
                status = PaymentStatus.PENDING,
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
