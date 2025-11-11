package com.loopers.domain.order

import com.loopers.domain.product.Product
import com.loopers.support.util.withId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("OrderTotalAmountCalculator 테스트")
class OrderTotalAmountCalculatorTest {

    @Test
    fun `여러 상품의 총 금액을 정확히 계산한다`() {
        // given
        val products = listOf(
            createProduct(id = 1L, price = 10000L),
            createProduct(id = 2L, price = 15000L),
            createProduct(id = 3L, price = 20000L),
        )
        val orderDetails = listOf(
            OrderCommand.OrderDetailCommand(productId = 1L, quantity = 2),
            OrderCommand.OrderDetailCommand(productId = 2L, quantity = 1),
            OrderCommand.OrderDetailCommand(productId = 3L, quantity = 3),
        )

        // when
        val totalAmount = OrderTotalAmountCalculator.calculate(
            items = orderDetails,
            products = products,
        )

        // then
        assertThat(totalAmount).isEqualTo(95000L)
    }

    @Test
    fun `주문 항목이 없을 때 0을 반환한다`() {
        // given
        val products = listOf(createProduct(id = 1L, price = 10000L))
        val emptyOrderDetails = emptyList<OrderCommand.OrderDetailCommand>()

        // when
        val totalAmount = OrderTotalAmountCalculator.calculate(
            items = emptyOrderDetails,
            products = products,
        )

        // then
        assertThat(totalAmount).isEqualTo(0L)
    }

    @Test
    fun `존재하지 않는 상품 ID로 계산 시도 시 예외가 발생한다`() {
        // given
        val products = listOf(createProduct(id = 1L, price = 10000L))
        val orderDetails = listOf(
            OrderCommand.OrderDetailCommand(productId = 999L, quantity = 1),
        )

        // when & then
        assertThatThrownBy {
            OrderTotalAmountCalculator.calculate(
                items = orderDetails,
                products = products,
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("상품 ID 999에 해당하는 상품을 찾을 수 없습니다.")
    }

    @Test
    fun `일부 상품만 존재하지 않을 때 예외가 발생한다`() {
        // given
        val products = listOf(
            createProduct(id = 1L, price = 10000L),
            createProduct(id = 2L, price = 15000L),
        )
        val orderDetails = listOf(
            OrderCommand.OrderDetailCommand(productId = 1L, quantity = 1),
            OrderCommand.OrderDetailCommand(productId = 999L, quantity = 1),
        )

        // when & then
        assertThatThrownBy {
            OrderTotalAmountCalculator.calculate(
                items = orderDetails,
                products = products,
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("상품 ID 999에 해당하는 상품을 찾을 수 없습니다.")
    }

    private fun createProduct(
        id: Long,
        price: Long,
        name: String = "테스트 상품",
        brandId: Long = 1L,
    ): Product {
        return Product.create(
            name = name,
            price = price,
            brandId = brandId,
        ).withId(id)
    }
}
