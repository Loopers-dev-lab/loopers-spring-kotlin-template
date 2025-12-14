package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.event.DomainEvent
import com.loopers.support.values.Money
import jakarta.persistence.AttributeOverride
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Transient

@Entity
@Table(name = "orders")
class Order(
    userId: Long,
    totalAmount: Money,
    status: OrderStatus,
    orderItems: MutableList<OrderItem> = mutableListOf(),
) : BaseEntity() {
    var userId: Long = userId
        private set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "total_amount", nullable = false))
    var totalAmount: Money = totalAmount
        private set

    @Enumerated(EnumType.STRING)
    var status: OrderStatus = status
        private set

    @OneToMany(
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY,
    )
    @JoinColumn(
        name = "order_id",
        nullable = false,
        foreignKey = ForeignKey(name = "fk_order_items_order_id"),
    )
    var orderItems: MutableList<OrderItem> = orderItems
        private set

    @Transient
    private var domainEvents: MutableList<DomainEvent>? = null

    private fun getDomainEvents(): MutableList<DomainEvent> {
        if (domainEvents == null) {
            domainEvents = mutableListOf()
        }
        return domainEvents!!
    }

    fun pollEvents(): List<DomainEvent> {
        val events = getDomainEvents().toList()
        getDomainEvents().clear()
        return events
    }

    init {
        if (totalAmount < Money.ZERO_KRW) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 금액은 0 이상이어야 합니다.")
        }
    }

    companion object {
        fun place(userId: Long): Order {
            return Order(userId, Money.ZERO_KRW, OrderStatus.PLACED)
        }

        fun of(
            userId: Long,
            totalAmount: Money,
            status: OrderStatus,
            orderItems: MutableList<OrderItem> = mutableListOf(),
        ): Order {
            return Order(userId, totalAmount, status, orderItems)
        }
    }

    fun addOrderItem(productId: Long, quantity: Int, productName: String, unitPrice: Money) {
        if (status != OrderStatus.PLACED) {
            throw CoreException(ErrorType.BAD_REQUEST, "PLACED 상태에서만 상품을 추가할 수 있습니다.")
        }

        orderItems.add(OrderItem.create(productId, quantity, productName, unitPrice))

        totalAmount = orderItems.fold(Money.ZERO_KRW) { acc, item ->
            acc + (item.unitPrice * item.quantity)
        }
    }

    fun pay() {
        if (status == OrderStatus.PAID) {
            return
        }
        if (status != OrderStatus.PLACED) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문이 완료되지 않았습니다.")
        }
        if (orderItems.count() == 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 상품이 없을 수 없습니다.")
        }
        if (totalAmount <= Money.ZERO_KRW) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 금액은 0보다 커야 합니다.")
        }

        status = OrderStatus.PAID
    }

    /**
     * 주문을 취소합니다. PLACED → CANCELLED 상태 전이
     * 멱등성: 이미 CANCELLED 상태면 아무 동작 없이 종료
     * @throws CoreException PLACED 상태가 아닌 경우
     */
    fun cancel() {
        if (status == OrderStatus.CANCELLED) {
            return
        }
        if (status != OrderStatus.PLACED) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 대기 상태에서만 취소할 수 있습니다")
        }
        status = OrderStatus.CANCELLED
        getDomainEvents().add(OrderCanceledEventV1.from(this))
    }
}
