package com.loopers.domain.order

import com.loopers.domain.product.Currency
import com.loopers.domain.product.Price
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderTest {
    @Test
    fun `주문을 생성할 수 있다`() {
        // given
        val userId = 1L
        val items = listOf(
            OrderItem(
                productId = 100L,
                productName = "운동화",
                brandId = 1L,
                brandName = "나이키",
                brandDescription = null,
                quantity = 2,
                priceAtOrder = Price(BigDecimal("100000"), Currency.KRW),
            ),
            OrderItem(
                productId = 101L,
                productName = "티셔츠",
                brandId = 1L,
                brandName = "나이키",
                brandDescription = null,
                quantity = 1,
                priceAtOrder = Price(BigDecimal("50000"), Currency.KRW),
            ),
        )

        // when
        val order = Order(userId = userId, items = items)

        // then
        assertThat(order.userId).isEqualTo(userId)
        assertThat(order.items).hasSize(2)
        assertThat(order.status).isEqualTo(OrderStatus.PENDING)
    }

    @Test
    fun `주문 항목이 비어있으면 예외가 발생한다`() {
        assertThatThrownBy {
            Order(userId = 1L, items = emptyList())
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("주문 항목")
    }

    @Test
    fun `총 주문 금액을 계산할 수 있다`() {
        // given
        val items = listOf(
            OrderItem(
                productId = 100L,
                productName = "운동화",
                brandId = 1L,
                brandName = "나이키",
                brandDescription = null,
                quantity = 2,
                priceAtOrder = Price(BigDecimal("100000"), Currency.KRW),
            ),
            OrderItem(
                productId = 101L,
                productName = "티셔츠",
                brandId = 1L,
                brandName = "나이키",
                brandDescription = null,
                quantity = 1,
                priceAtOrder = Price(BigDecimal("50000"), Currency.KRW),
            ),
        )
        val order = Order(userId = 1L, items = items)

        // when
        val totalAmount = order.calculateTotalAmount()

        // then
        assertThat(totalAmount.amount).isEqualTo(BigDecimal("250000")) // 200000 + 50000
    }

    @Test
    fun `주문 소유자를 확인할 수 있다`() {
        // given
        val userId = 1L
        val items = listOf(
            OrderItem(
                productId = 100L,
                productName = "운동화",
                brandId = 1L,
                brandName = "나이키",
                brandDescription = null,
                quantity = 1,
                priceAtOrder = Price(BigDecimal("100000"), Currency.KRW),
            ),
        )
        val order = Order(userId = userId, items = items)

        // when & then
        assertThat(order.isOwnedBy(userId)).isTrue()
        assertThat(order.isOwnedBy(999L)).isFalse()
    }

    @Test
    fun `주문을 취소할 수 있다`() {
        // given
        val items = listOf(
            OrderItem(
                productId = 100L,
                productName = "운동화",
                brandId = 1L,
                brandName = "나이키",
                brandDescription = null,
                quantity = 1,
                priceAtOrder = Price(BigDecimal("100000"), Currency.KRW),
            ),
        )
        val order = Order(userId = 1L, items = items)

        // when
        order.cancel()

        // then
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
    }
}
