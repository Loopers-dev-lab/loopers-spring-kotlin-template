package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.member.Member
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
    discountAmount: Money = Money.zero(),
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

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "discount_amount", nullable = false))
    var discountAmount: Money = discountAmount
        protected set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "final_amount", nullable = false))
    var finalAmount: Money = Money.zero()
        protected set

    @OneToMany(mappedBy = "order", cascade = [jakarta.persistence.CascadeType.ALL], orphanRemoval = true)
    protected val mutableItems: MutableList<OrderItem> = items.toMutableList()

    val items: List<OrderItem>
        get() = mutableItems.toList()

    init {
        items.forEach { it.assignOrder(this) }
        this.totalAmount = calculateTotalAmount()
        this.finalAmount = this.totalAmount.minus(this.discountAmount)
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

    /**
     * 포인트 사용 가능 여부 검증
     */
    fun validatePointUsage(usePoint: Long) {
        if (usePoint > finalAmount.amount) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "포인트를 너무 많이 사용했습니다. 최종 금액: ${finalAmount.amount}, 사용 포인트: $usePoint"
            )
        }
    }

    companion object {
        fun create(
            memberId: String,
            orderItems: List<OrderItemCommand>,
            productMap: Map<Long, Product>,
            discountAmount: Money = Money.zero(),
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

                // 주문 시점의 스냅샷 데이터 전달
                OrderItem.of(
                    productId = product.id,
                    productName = product.name,
                    price = product.price,
                    quantity = quantity
                )
            }

            return Order(memberId, items, discountAmount)
        }
    }
}
