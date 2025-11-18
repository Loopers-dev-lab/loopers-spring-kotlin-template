package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.Product
import com.loopers.domain.product.Quantity
import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "orders")
class Order(
    memberId: String,
    items: List<OrderItem> = emptyList(),
) : BaseEntity() {

    @Column(name = "member_id", nullable = false, length = 50)
    var memberId: String = memberId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.PENDING
        protected set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "total_amount", nullable = false))
    var totalAmount: Money = Money.zero()
        protected set

    @OneToMany(mappedBy = "order", cascade = [jakarta.persistence.CascadeType.ALL], orphanRemoval = true)
    protected val mutableItems: MutableList<OrderItem> = items.toMutableList()

    val items: List<OrderItem>
        get() = mutableItems.toList()

    init {
        items.forEach { it.assignOrder(this) }
        this.totalAmount = calculateTotalAmount()
    }

    fun addItem(items: OrderItem) {
        mutableItems.add(items)
        items.assignOrder(this)
        this.totalAmount = calculateTotalAmount()
    }

    fun calculateTotalAmount() : Money {
        return mutableItems
            .map { it.subtotal }
            .fold(Money.zero()) {acc, money -> acc.plus(money)}
    }

    fun complete() {
        if (status != OrderStatus.PENDING) {
            throw CoreException(
                ErrorType.INVALID_ORDER_STATUS,
                "주문 완료 처리할 수 없는 상태입니다. 현재 상태: $status"
            )
        }
        status = OrderStatus.COMPLETED
    }

    fun fail() {
        if (status != OrderStatus.PENDING) {
            throw CoreException(
                ErrorType.INVALID_ORDER_STATUS,
                "주문 실패 처리할 수 없는 상태입니다. 현재 상태: $status"
            )
        }
        status = OrderStatus.FAILED
    }

    fun cancel() {
        if (status != OrderStatus.PENDING) {
            throw CoreException(
                ErrorType.INVALID_ORDER_STATUS,
                "완료된 주문만 취소할 수 있습니다. 현재 상태: $status",
            )
        }
        status = OrderStatus.CANCELLED
    }

    companion object {
        fun create(
            memberId: String,
            orderItems: List<OrderItemCommand>,
            productMap: Map<Long, Product>
        ): Order {
            val items = orderItems.map { itemCommand ->
                val product = productMap[itemCommand.productId]
                    ?: throw CoreException(
                        ErrorType.PRODUCT_NOT_FOUND,
                        "상품을 찾을 수 없습니다. id: ${itemCommand.productId}"
                    )

                val quantity = Quantity.of(itemCommand.quantity)

                // 재고 검증
                product.validateStock(quantity)

                OrderItem.of(product, quantity)
            }

            return Order(memberId, items)
        }
    }
}
