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
        val brand = Brand("테스트브랜드", "설명")
        val product = Product("상품1", "설명", Money.of(1000L), Stock.of(100), brand)
        val quantity = Quantity.of(3)

        val orderItem = OrderItem.of(product, quantity)

        assertAll(
            { assertThat(orderItem.product).isEqualTo(product) },
            { assertThat(orderItem.quantity).isEqualTo(quantity) },
            { assertThat(orderItem.price.amount).isEqualTo(1000L) },
            { assertThat(orderItem.subtotal.amount).isEqualTo(3000L) },
        )
    }

    @DisplayName("주문 아이템 생성 시 소계가 자동 계산된다")
    @Test
    fun calculateSubtotalOnCreate() {
        val brand = Brand("테스트브랜드", "설명")
        val product = Product("상품1", "설명", Money.of(15000L), Stock.of(100), brand)
        val quantity = Quantity.of(5)

        val orderItem = OrderItem.of(product, quantity)

        assertThat(orderItem.subtotal.amount).isEqualTo(75000L)
    }
    
    @DisplayName("주문 아이템에 주문을 할당할 수 있다")
    @Test
    fun assignOrderToOrderItem() {
        val brand = Brand("테스트브랜드", "설명")
        val product = Product("상품1", "설명", Money.of(10000L), Stock.of(100), brand)
        val quantity = Quantity.of(2)
        val orderItem = OrderItem.of(product, quantity)
        
        val order = Order("member1", emptyList())
        orderItem.assignOrder(order)

        assertThat(orderItem.order).isEqualTo(order)
    }


}
