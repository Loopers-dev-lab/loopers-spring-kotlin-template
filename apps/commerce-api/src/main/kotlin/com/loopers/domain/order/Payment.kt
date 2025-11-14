package com.loopers.domain.order

import com.loopers.domain.BaseEntity
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

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: PaymentStatus = status

    companion object {
        fun paid(
            userId: Long,
            order: Order,
            usedPoint: Money,
        ): Payment {
            return Payment(
                orderId = order.id,
                userId = userId,
                totalAmount = order.totalAmount,
                usedPoint = usedPoint,
                status = PaymentStatus.PAID,
            )
        }

        fun of(
            orderId: Long,
            userId: Long,
            totalAmount: Money,
            usedPoint: Money,
            status: PaymentStatus,
        ): Payment {
            return Payment(
                orderId = orderId,
                userId = userId,
                totalAmount = totalAmount,
                usedPoint = usedPoint,
                status = status,
            )
        }
    }
}
