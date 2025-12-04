package com.loopers.domain.order

import com.loopers.application.order.OrderCommand
import com.loopers.application.order.OrderItemCommand
import com.loopers.domain.BaseEntity
import com.loopers.domain.common.vo.Money
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "orders")
class OrderModel(
    @Column
    val refUserId: Long,

    @Column
    val orderKey: String,
) : BaseEntity() {

    @Column
    var status: OrderStatus = OrderStatus.PENDING

    @Column
    var totalPrice: Money = Money(BigDecimal.ZERO)

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "ref_order_id")
    val orderItems: MutableList<OrderItemModel> = mutableListOf()

    fun addOrderItems(items: List<OrderItemCommand>) {
        items.forEach { item ->
            val orderItem = OrderItemModel(
                refProductId = item.productId,
                quantity = item.quantity,
                productPrice = Money(item.productPrice),
            )
            orderItems.add(orderItem)
        }
    }

    fun updateTotalPrice() {
        this.totalPrice = orderItems
            .map { it.productPrice.amount.multiply(it.quantity.toBigDecimal()) }
            .fold(BigDecimal.ZERO) { acc, price -> acc.add(price) }
            .let { Money(it) }
    }

    fun complete() {
        this.status = OrderStatus.COMPLETE
    }

    fun requestPayment() {
        this.status = OrderStatus.PAY_PENDING
    }

    fun fail() {
        this.status = OrderStatus.PAY_FAIL
    }

    companion object {
        fun order(refUserId: Long, command: OrderCommand): OrderModel {
            val orderKey = createOrderKey()
            val order = OrderModel(refUserId, orderKey)
            order.addOrderItems(command.orderItems)
            order.updateTotalPrice()
            return order
        }

        private fun createOrderKey(): String = UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 12)
            .uppercase()
    }
}
