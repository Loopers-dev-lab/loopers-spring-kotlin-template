package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class OrderItemTest {

    @DisplayName("주문 상품 생성 테스트")
    @Nested
    inner class Create {

        @DisplayName("유효한 값으로 주문 상품이 생성된다")
        @Test
        fun `create order item with valid values`() {
            // given
            val productId = 1L
            val quantity = 5
            val productName = "맥북 프로"
            val unitPrice = Money.krw(2000000)

            // when
            val orderItem = OrderItem.create(
                productId = productId,
                quantity = quantity,
                productName = productName,
                unitPrice = unitPrice,
            )

            // then
            assertThat(orderItem.productId).isEqualTo(productId)
            assertThat(orderItem.quantity).isEqualTo(quantity)
            assertThat(orderItem.productName).isEqualTo(productName)
            assertThat(orderItem.unitPrice).isEqualTo(unitPrice)
        }

        @DisplayName("수량이 0일 때 예외가 발생한다")
        @Test
        fun `create order item when quantity is zero`() {
            // given
            val quantity = 0

            // when
            val exception = assertThrows<CoreException> {
                createOrderItem(quantity = quantity)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("주문 수량은 1 이상이어야 합니다.")
        }

        @DisplayName("수량이 음수일 때 예외가 발생한다")
        @Test
        fun `throws exception when quantity is negative`() {
            // given
            val negativeQuantity = -1

            // when
            val exception = assertThrows<CoreException> {
                createOrderItem(quantity = negativeQuantity)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("주문 수량은 1 이상이어야 합니다.")
        }

        @DisplayName("단가가 0일 때 예외가 발생한다")
        @Test
        fun `throws exception when unit price is zero`() {
            // given
            val zeroPrice = Money.ZERO_KRW

            // when
            val exception = assertThrows<CoreException> {
                createOrderItem(unitPrice = zeroPrice)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("단가는 0 보다 커야 합니다.")
        }

        @DisplayName("단가가 음수일 때 예외가 발생한다")
        @Test
        fun `throws exception when unit price is negative`() {
            // given
            val negativePrice = Money.krw(-1000)

            // when
            val exception = assertThrows<CoreException> {
                createOrderItem(unitPrice = negativePrice)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("단가는 0 보다 커야 합니다.")
        }
    }

    private fun createOrderItem(
        productId: Long = 1L,
        quantity: Int = 1,
        productName: String = "테스트 상품",
        unitPrice: Money = Money.krw(10000),
    ): OrderItem {
        return OrderItem.create(
            productId = productId,
            quantity = quantity,
            productName = productName,
            unitPrice = unitPrice,
        )
    }
}
