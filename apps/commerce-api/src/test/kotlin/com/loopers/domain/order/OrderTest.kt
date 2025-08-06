package com.loopers.domain.order

import com.loopers.domain.order.entity.Order
import com.loopers.domain.order.entity.Order.Status.ORDER_REQUEST
import com.loopers.domain.order.entity.Order.Status.ORDER_SUCCESS
import com.loopers.domain.order.entity.Order.Status.PAYMENT_REQUEST
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.test.Test

class OrderTest {
    @Test
    fun `주문을 생성할 수 있다`() {
        // when
        val order = Order.create(1L, BigDecimal("10000"), BigDecimal("10000"), ORDER_REQUEST)

        // then
        assertThat(order.userId).isEqualTo(1L)
        assertThat(order.originalPrice.value).isEqualTo("10000")
        assertThat(order.finalPrice.value).isEqualTo("10000")
        assertThat(order.status).isEqualTo(ORDER_REQUEST)
    }

    @Test
    fun `ORDER_REQUEST 상태일 때 결제 요청으로 상태를 변경할 수 있다`() {
        val order = Order.create(1L, BigDecimal("10000"), BigDecimal("10000"), ORDER_REQUEST)

        order.paymentRequest()

        assertThat(order.status).isEqualTo(PAYMENT_REQUEST)
    }

    @Test
    fun `PAYMENT_REQUEST 상태일 때 주문을 성공 처리할 수 있다`() {
        val order = Order.create(1L, BigDecimal("10000"), BigDecimal("10000"), PAYMENT_REQUEST)

        order.success()

        assertThat(order.status).isEqualTo(ORDER_SUCCESS)
    }

    @Test
    fun `PAYMENT_REQUEST 상태일 때 주문을 실패 처리할 수 있다`() {
        val order = Order.create(1L, BigDecimal("10000"), BigDecimal("10000"), PAYMENT_REQUEST)

        order.failure("failReason")

        assertThat(order.status).isEqualTo(Order.Status.ORDER_FAIL)
    }

    @Test
    fun `ORDER_REQUEST 상태가 아니면 결제 요청 시 예외가 발생한다`() {
        val order = Order.create(1L, BigDecimal("10000"), BigDecimal("10000"), PAYMENT_REQUEST)

        val exception = assertThrows<CoreException> {
            order.paymentRequest()
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
    }

    @Test
    fun `PAYMENT_REQUEST 상태가 아니면 주문 성공 처리 시 예외가 발생한다`() {
        val order = Order.create(1L, BigDecimal("10000"), BigDecimal("10000"), ORDER_REQUEST)

        val exception = assertThrows<CoreException> {
            order.success()
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
    }

    @Test
    fun `PAYMENT_REQUEST 상태가 아니면 주문 실패 처리 시 예외가 발생한다`() {
        val order = Order.create(1L, BigDecimal("10000"), BigDecimal("10000"), ORDER_SUCCESS)

        val exception = assertThrows<CoreException> {
            order.failure("failReason")
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
    }
}
