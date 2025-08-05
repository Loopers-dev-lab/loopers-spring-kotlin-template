package com.loopers.domain.order

import com.loopers.domain.order.entity.OrderItem
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class OrderItemTest {

    @Test
    fun `주문 아이템을 생성할 수 있다`() {
        // when
        val orderItem = OrderItem.create(1L, 1L, 1)

        // then
        assertThat(orderItem.orderId).isEqualTo(1L)
        assertThat(orderItem.productOptionId).isEqualTo(1L)
        assertThat(orderItem.quantity.value).isEqualTo(1)
    }

    @Test
    fun `주문 아이템 생성 시 수량이 0 보다 작으면 예외가 발생한다`() {
        // when & then
        val exception = assertThrows<CoreException> {
            OrderItem.create(1L, 10L, -1)
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }
}
