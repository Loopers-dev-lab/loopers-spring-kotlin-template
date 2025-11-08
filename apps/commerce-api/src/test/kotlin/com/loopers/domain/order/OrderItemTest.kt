package com.loopers.domain.order

import com.loopers.domain.product.Currency
import com.loopers.domain.product.Price
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderItemTest {
    @Test
    fun `주문 항목을 생성할 수 있다`() {
        // given
        val productId = 100L
        val productName = "운동화"
        val brandId = 1L
        val brandName = "나이키"
        val brandDescription = "스포츠 브랜드"
        val quantity = 2
        val priceAtOrder = Price(BigDecimal("100000"), Currency.KRW)

        // when
        val orderItem = OrderItem(
            productId = productId,
            productName = productName,
            brandId = brandId,
            brandName = brandName,
            brandDescription = brandDescription,
            quantity = quantity,
            priceAtOrder = priceAtOrder
        )

        // then
        assertThat(orderItem.productId).isEqualTo(productId)
        assertThat(orderItem.productName).isEqualTo(productName)
        assertThat(orderItem.brandId).isEqualTo(brandId)
        assertThat(orderItem.brandName).isEqualTo(brandName)
        assertThat(orderItem.quantity).isEqualTo(quantity)
        assertThat(orderItem.priceAtOrder).isEqualTo(priceAtOrder)
    }

    @Test
    fun `수량은 1개 이상이어야 한다`() {
        assertThatThrownBy {
            OrderItem(
                productId = 100L,
                productName = "운동화",
                brandId = 1L,
                brandName = "나이키",
                brandDescription = null,
                quantity = 0,
                priceAtOrder = Price(BigDecimal("100000"), Currency.KRW)
            )
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("수량")
    }

    @Test
    fun `항목별 금액을 계산할 수 있다`() {
        // given
        val orderItem = OrderItem(
            productId = 100L,
            productName = "운동화",
            brandId = 1L,
            brandName = "나이키",
            brandDescription = null,
            quantity = 3,
            priceAtOrder = Price(BigDecimal("100000"), Currency.KRW)
        )

        // when
        val itemAmount = orderItem.calculateItemAmount()

        // then
        assertThat(itemAmount.amount).isEqualTo(BigDecimal("300000"))
    }
}
