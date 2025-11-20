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

@Table(name = "payments")
@Entity
class Payment(
    orderId: Long,
    userId: Long,
    totalAmount: Money,
    usedPoint: Money,
    status: PaymentStatus,
    issuedCouponId: Long? = null,
    couponDiscount: Money = Money.ZERO_KRW,
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

    companion object {
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
                status = PaymentStatus.PAID,
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
        ): Payment {
            return Payment(
                orderId = orderId,
                userId = userId,
                totalAmount = totalAmount,
                usedPoint = usedPoint,
                status = status,
                issuedCouponId = issuedCouponId,
                couponDiscount = couponDiscount,
            )
        }
    }
}
