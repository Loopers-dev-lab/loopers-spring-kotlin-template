package com.loopers.domain.order

import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import com.loopers.domain.product.Quantity
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class OrderItemTest {

    @DisplayName("주문 아이템을 생성할 수 있다")
    @Test
    fun createOrderItem() {
        val productId = 1L
        val productName = "상품1"
        val price = Money.of(1000L)
        val quantity = Quantity.of(3)

        val orderItem = OrderItem.of(productId, productName, price, quantity)

        assertAll(
            { assertThat(orderItem.productId).isEqualTo(productId) },
            { assertThat(orderItem.productName).isEqualTo(productName) },
            { assertThat(orderItem.quantity).isEqualTo(quantity) },
            { assertThat(orderItem.price.amount).isEqualTo(1000L) },
            { assertThat(orderItem.subtotal.amount).isEqualTo(3000L) },
        )
    }

    @DisplayName("주문 아이템 생성 시 소계가 자동 계산된다")
    @Test
    fun calculateSubtotalOnCreate() {
        val orderItem = OrderItem.of(1L, "상품1", Money.of(15000L), Quantity.of(5))

        assertThat(orderItem.subtotal.amount).isEqualTo(75000L)
    }
    
    @DisplayName("주문 아이템에 주문을 할당할 수 있다")
    @Test
    fun assignOrderToOrderItem() {
        val orderItem = OrderItem.of(1L, "상품1", Money.of(10000L), Quantity.of(2))
        
        val order = Order("member1", emptyList())
        orderItem.assignOrder(order)

        assertThat(orderItem.order).isEqualTo(order)
    }


}
