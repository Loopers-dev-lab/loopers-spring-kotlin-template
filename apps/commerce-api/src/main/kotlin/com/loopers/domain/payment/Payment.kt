package com.loopers.domain.payment

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
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

@Table(
    name = "payments",
    indexes = [
        Index(name = "idx_payment_status_created_at", columnList = "status, created_at"),
        Index(name = "idx_payment_external_payment_key", columnList = "external_payment_key", unique = true),
    ],
)
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
    cardInfo: CardInfo? = null,
    externalPaymentKey: String? = null,
    failureMessage: String? = null,
    attemptedAt: Instant? = null,
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

    @Embedded
    var cardInfo: CardInfo? = cardInfo
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

    @Column(name = "attempted_at")
    var attemptedAt: Instant? = attemptedAt
        private set

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        private set

    /**
     * 결제를 개시합니다. PENDING → IN_PROGRESS, PAID, 또는 FAILED 상태 전이
     * PG 결제 요청 결과에 따라 상태가 결정됩니다.
     * - Accepted: IN_PROGRESS, transactionKey 저장 -> InProgress
     * - Uncertain: IN_PROGRESS, transactionKey null 유지 -> InProgress
     * - NotReached: FAILED (PG 서비스 불능) -> Failed
     * - NotRequired: PAID (0원 결제) -> NotRequired
     *
     * @param result PG 결제 요청 결과
     * @param attemptedAt 결제 시도 시각
     * @return PgPaymentResult 결제 개시 결과 (InProgress, NotRequired, 또는 Failed)
     * @throws CoreException PENDING 상태가 아닌 경우
     */
    fun initiate(result: PgPaymentCreateResult, attemptedAt: Instant): PgPaymentResult {
        if (status != PaymentStatus.PENDING) {
            throw CoreException(ErrorType.BAD_REQUEST, "결제 대기 상태에서만 결제를 개시할 수 있습니다")
        }
        this.attemptedAt = attemptedAt

        return when (result) {
            is PgPaymentCreateResult.Accepted -> {
                status = PaymentStatus.IN_PROGRESS
                externalPaymentKey = result.transactionKey
                PgPaymentResult.InProgress(this)
            }

            is PgPaymentCreateResult.Uncertain -> {
                status = PaymentStatus.IN_PROGRESS
                PgPaymentResult.InProgress(this)
            }

            is PgPaymentCreateResult.NotReached -> {
                status = PaymentStatus.FAILED
                failureMessage = "PG 서비스에 연결할 수 없습니다"
                PgPaymentResult.Failed(this, failureMessage ?: "알 수 없는 오류")
            }

            is PgPaymentCreateResult.NotRequired -> {
                status = PaymentStatus.PAID
                PgPaymentResult.NotRequired(this)
            }
        }
    }

    /**
     * PG 트랜잭션 결과로 결제 상태를 확정합니다.
     *
     * 멱등성: 이미 PAID/FAILED 상태면 해당 결과를 그대로 반환
     *
     * 매칭 우선순위:
     * 1. externalPaymentKey가 있으면 해당 키와 일치하는 transaction
     * 2. externalPaymentKey가 없으면 paidAmount와 일치하고 PENDING이 아닌 transaction
     *
     * @param transactions PG에서 조회한 트랜잭션 목록
     * @param currentTime 현재 시각 (타임아웃 판단용)
     * @return ConfirmResult - Paid, Failed, 또는 StillInProgress
     * @throws CoreException PENDING 상태인 경우 (아직 결제 시작 안됨)
     */
    fun confirmPayment(transactions: List<PgTransaction>, currentTime: Instant): ConfirmResult {
        // 멱등성: 이미 최종 상태면 해당 결과 반환
        if (status == PaymentStatus.PAID) {
            return ConfirmResult.Paid(this)
        }
        if (status == PaymentStatus.FAILED) {
            return ConfirmResult.Failed(this)
        }

        // PENDING 상태면 예외 (아직 결제 시작 안됨)
        if (status != PaymentStatus.IN_PROGRESS) {
            throw CoreException(ErrorType.BAD_REQUEST, "결제 진행 중 상태에서만 확정할 수 있습니다")
        }

        val matched = findMatchingTransaction(transactions)

        when {
            matched?.status == PgTransactionStatus.SUCCESS -> {
                status = PaymentStatus.PAID
                externalPaymentKey = matched.transactionKey
            }

            matched?.status == PgTransactionStatus.FAILED -> {
                status = PaymentStatus.FAILED
                externalPaymentKey = matched.transactionKey
                failureMessage = matched.failureReason
            }

            matched == null -> {
                status = PaymentStatus.FAILED
                failureMessage = "매칭되는 PG 트랜잭션이 없습니다"
            }

            isTimedOut(currentTime) -> {
                status = PaymentStatus.FAILED
                failureMessage = "결제 시간 초과"
            }
        }

        return when (status) {
            PaymentStatus.PAID -> ConfirmResult.Paid(this)
            PaymentStatus.FAILED -> ConfirmResult.Failed(this)
            else -> ConfirmResult.StillInProgress(this)
        }
    }

    private fun findMatchingTransaction(transactions: List<PgTransaction>): PgTransaction? {
        return if (externalPaymentKey != null) {
            transactions.find { it.transactionKey == externalPaymentKey }
        } else {
            transactions.find {
                it.paymentId == this.id
            }
        }
    }

    private fun isTimedOut(currentTime: Instant): Boolean {
        val timeout = attemptedAt?.plusSeconds(300) ?: return false
        return currentTime.isAfter(timeout)
    }

    companion object {
        /**
         * 결제를 생성합니다.
         * paidAmount = totalAmount - usedPoint - couponDiscount 로 자동 계산
         * 모든 결제는 PENDING 상태로 생성됩니다. (0원 결제 포함)
         *
         * @param cardInfo 카드 정보 (paidAmount > 0인 경우 필수, 0원 결제인 경우 null 가능)
         */
        fun create(
            userId: Long,
            orderId: Long,
            totalAmount: Money,
            usedPoint: Money,
            issuedCouponId: Long?,
            couponDiscount: Money,
            cardInfo: CardInfo? = null,
        ): Payment {
            if (usedPoint < Money.ZERO_KRW) {
                throw CoreException(ErrorType.BAD_REQUEST, "사용 포인트는 0 이상이어야 합니다")
            }
            if (couponDiscount < Money.ZERO_KRW) {
                throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 할인은 0 이상이어야 합니다")
            }

            val paidAmount = totalAmount - usedPoint - couponDiscount

            if (paidAmount < Money.ZERO_KRW) {
                throw CoreException(ErrorType.BAD_REQUEST, "포인트와 쿠폰 할인의 합이 주문 금액을 초과합니다")
            }

            if (paidAmount > Money.ZERO_KRW && cardInfo == null) {
                throw CoreException(ErrorType.BAD_REQUEST, "카드 결제 시 카드 정보는 필수입니다")
            }

            return Payment(
                orderId = orderId,
                userId = userId,
                totalAmount = totalAmount,
                usedPoint = usedPoint,
                paidAmount = paidAmount,
                status = PaymentStatus.PENDING,
                issuedCouponId = issuedCouponId,
                couponDiscount = couponDiscount,
                cardInfo = cardInfo,
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
            cardInfo: CardInfo? = null,
            paidAmount: Money = Money.ZERO_KRW,
            externalPaymentKey: String? = null,
            failureMessage: String? = null,
            attemptedAt: Instant? = null,
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
                cardInfo = cardInfo,
                externalPaymentKey = externalPaymentKey,
                failureMessage = failureMessage,
                attemptedAt = attemptedAt,
            )
        }
    }
}
