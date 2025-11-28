package com.loopers.domain.order

import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import com.loopers.domain.product.Quantity
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class OrderTest {

    @DisplayName("주문을 생성할 수 있다")
    @Test
    fun createOrder() {
        val orderItem = OrderItem.of(1L, "상품1", Money.of(10000L), Quantity.of(2))

        val order = Order("member1", listOf(orderItem))

        assertAll(
            { assertThat(order.memberId).isEqualTo("member1") },
            { assertThat(order.status).isEqualTo(OrderStatus.PENDING) },
            { assertThat(order.items).hasSize(1) },
            { assertThat(order.totalAmount.amount).isEqualTo(20000L) },
        )
    }

    @DisplayName("주문 생성 시 총액이 계산된다")
    @Test
    fun calculateTotalAmountOnCreate() {
        val orderItem1 = OrderItem.of(1L, "상품1", Money.of(10000L), Quantity.of(2))
        val orderItem2 = OrderItem.of(2L, "상품2", Money.of(20000L), Quantity.of(1))

        val order = Order("member1", listOf(orderItem1, orderItem2))

        assertThat(order.totalAmount.amount).isEqualTo(40000L)
    }

    @DisplayName("주문에 아이템을 추가할 수 있다")
    @Test
    fun addItemToOrder() {
        val orderItem1 = OrderItem.of(1L, "상품1", Money.of(10000L), Quantity.of(2))
        val order = Order("member1", listOf(orderItem1))

        val orderItem2 = OrderItem.of(2L, "상품2", Money.of(20000L), Quantity.of(1))
        order.addItem(orderItem2)

        assertThat(order.items).hasSize(2)
        assertThat(order.totalAmount.amount).isEqualTo(40000L)
    }

    @DisplayName("주문을 완료 처리할 수 있다")
    @Test
    fun completeOrder() {
        val orderItem = OrderItem.of(1L, "상품1", Money.of(10000L), Quantity.of(2))
        val order = Order("member1", listOf(orderItem))

        order.complete()

        assertThat(order.status).isEqualTo(OrderStatus.COMPLETED)
    }

    @DisplayName("PENDING 상태가 아닌 주문을 완료 처리하면 예외가 발생한다")
    @Test
    fun failToCompleteOrderIfNotPending() {
        val orderItem = OrderItem.of(1L, "상품1", Money.of(10000L), Quantity.of(2))
        val order = Order("member1", listOf(orderItem))

        order.complete()

        val exception = assertThrows<CoreException> {
            order.complete()
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_ORDER_STATUS)
    }

    @DisplayName("주문을 실패 처리할 수 있다")
    @Test
    fun failOrder() {
        val orderItem = OrderItem.of(1L, "상품1", Money.of(10000L), Quantity.of(2))
        val order = Order("member1", listOf(orderItem))

        order.fail()

        assertThat(order.status).isEqualTo(OrderStatus.FAILED)
    }

    @DisplayName("PENDING 상태가 아닌 주문을 실패 처리하면 예외가 발생한다")
    @Test
    fun failToFailOrderIfNotPending() {
        val orderItem = OrderItem.of(1L, "상품1", Money.of(10000L), Quantity.of(2))
        val order = Order("member1", listOf(orderItem))

        order.complete()

        val exception = assertThrows<CoreException> {
            order.fail()
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_ORDER_STATUS)
    }

    @DisplayName("주문을 취소 처리할 수 있다")
    @Test
    fun cancelOrder() {
        val orderItem = OrderItem.of(1L, "상품1", Money.of(10000L), Quantity.of(2))
        val order = Order("member1", listOf(orderItem))

        order.cancel()

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
    }

    @DisplayName("PENDING 상태가 아닌 주문을 취소 처리하면 예외가 발생한다")
    @Test
    fun failToCancelOrderIfNotPending() {
        val orderItem = OrderItem.of(1L, "상품1", Money.of(10000L), Quantity.of(2))
        val order = Order("member1", listOf(orderItem))

        order.complete()

        val exception = assertThrows<CoreException> {
            order.cancel()
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_ORDER_STATUS)
    }

    @DisplayName("존재하지 않는 상품으로 주문 생성 시 예외가 발생한다")
    @Test
    fun failToCreateOrderWithInvalidProduct() {
        val orderItemCommands = listOf(
            OrderItemCommand(999L, 2)
        )

        val productMap = emptyMap<Long, Product>()

        val exception = assertThrows<CoreException> {
            Order.create("member1", orderItemCommands, productMap)
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.PRODUCT_NOT_FOUND)
    }


    @DisplayName("재고가 부족한 상품으로 주문 생성 시 예외가 발생한다")
    @Test
    fun failToCreateOrderWithInsufficientStock() {
        val product = Product("상품1", "설명", Money.of(10000L), Stock.of(5), 1L)

        val orderItemCommands = listOf(
            OrderItemCommand(product.id!!, 10)
        )

        val productMap = mapOf(
            product.id!! to product
        )

        val exception = assertThrows<CoreException> {
            Order.create("member1", orderItemCommands, productMap)
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.INSUFFICIENT_STOCK)
    }



}
