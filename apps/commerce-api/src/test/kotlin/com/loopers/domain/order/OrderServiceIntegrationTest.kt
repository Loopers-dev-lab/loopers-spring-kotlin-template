package com.loopers.domain.order

import com.loopers.domain.order.dto.command.OrderCommand
import com.loopers.domain.order.entity.Order
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

@SpringBootTest
class OrderServiceIntegrationTest @Autowired constructor(
    private val orderService: OrderService,
    private val orderRepository: OrderRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("상세 조회")
    @Nested
    inner class Get {

        @Test
        fun `id로 주문을 조회한다`() {
            // given
            val create = Order.create(1L, BigDecimal("1"), BigDecimal("1"), Order.Status.ORDER_REQUEST)
            val order = orderRepository.save(create)

            // when
            val found = orderService.get(order.id)

            // then
            assertThat(found.id).isEqualTo(order.id)
        }

        @Test
        fun `존재하지 않는 id를 조회하면 예외가 발생한다`() {
            // expect
            assertThrows<CoreException> {
                orderService.get(-1L)
            }
        }
    }

    @DisplayName("주문 등록")
    @Nested
    inner class Request {

        @Test
        fun `주문을 정상적으로 등록한다`() {
            // given
            val command = OrderCommand.RequestOrder(1L, BigDecimal("1"), BigDecimal("1"), Order.Status.ORDER_REQUEST, listOf())

            // when
            val saved = orderService.request(command)

            // then
            val found = orderRepository.find(saved.id)!!
            assertThat(found.userId).isEqualTo(command.userId)
            assertThat(found.originalPrice.value.stripTrailingZeros())
                .isEqualTo(command.originalPrice.stripTrailingZeros())
            assertThat(found.finalPrice.value.stripTrailingZeros())
                .isEqualTo(command.finalPrice.stripTrailingZeros())
            assertThat(found.status).isEqualTo(command.status)
        }

        @Test
        fun `originalPrice가 0보다 작으면 예외가 발생한다`() {
            // given
            val command = OrderCommand.RequestOrder(1L, BigDecimal("-1"), BigDecimal("1"), Order.Status.ORDER_REQUEST, listOf())

            // expect
            assertThrows<CoreException> {
                orderService.request(command)
            }
        }

        @Test
        fun `finalPrice가 0보다 작으면 예외가 발생한다`() {
            // given
            val command = OrderCommand.RequestOrder(1L, BigDecimal("1"), BigDecimal("-1"), Order.Status.ORDER_REQUEST, listOf())

            // expect
            assertThrows<CoreException> {
                orderService.request(command)
            }
        }
    }
}
