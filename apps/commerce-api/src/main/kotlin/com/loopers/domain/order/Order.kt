package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.Currency
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "orders")
class Order(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    items: List<OrderItem>
) : BaseEntity() {
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT))
    val items: MutableList<OrderItem> = items.toMutableList()

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "total_amount", nullable = false, precision = 15, scale = 2)),
        AttributeOverride(name = "currency", column = Column(name = "currency", nullable = false, length = 3))
    )
    var totalAmount: Money = calculateTotalAmount()
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.PENDING
        protected set

    init {
        require(items.isNotEmpty()) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 항목은 최소 1개 이상이어야 합니다.")
        }
        this.totalAmount = calculateTotalAmount()
    }

    fun calculateTotalAmount(): Money {
        if (items.isEmpty()) {
            return Money(BigDecimal.ZERO, Currency.KRW)
        }

        val firstCurrency = items.first().priceAtOrder.currency
        val totalAmount = items.fold(BigDecimal.ZERO) { acc, item ->
            acc + item.calculateItemAmount().amount
        }

        return Money(amount = totalAmount, currency = firstCurrency)
    }

    fun isOwnedBy(userId: Long): Boolean {
        return this.userId == userId
    }

    fun cancel() {
        this.status = OrderStatus.CANCELLED
    }

    fun confirm() {
        this.status = OrderStatus.CONFIRMED
    }
}
