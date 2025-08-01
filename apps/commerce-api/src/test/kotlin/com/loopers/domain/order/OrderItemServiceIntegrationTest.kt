package com.loopers.domain.order

import com.loopers.domain.order.dto.command.OrderItemCommand
import com.loopers.domain.order.entity.OrderItem
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

@SpringBootTest
class OrderItemServiceIntegrationTest @Autowired constructor(
    private val orderItemService: OrderItemService,
    private val orderItemRepository: OrderItemRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("등록")
    @Nested
    inner class Register {
        @Test
        fun `주문 아이템을 등록한다`() {
            // given
            val command = OrderItemCommand
                .Register(
                    1L,
                listOf(
                    OrderItemCommand.Register.Item(1L, 2),
                    OrderItemCommand.Register.Item(2L, 3),
                ),
            )

            // when
            val saved = orderItemService.register(command)

            // then
            assertThat(saved).hasSize(2)
            assertThat(saved[0].orderId).isEqualTo(1L)
            assertThat(saved[0].productOptionId).isEqualTo(1L)
            assertThat(saved[0].quantity.value).isEqualTo(2)
        }

        @Test
        fun `orderId, orderItemId가 중복된 값으로 등록 시 예외가 발생한다`() {
            // given
            val command = OrderItemCommand
                .Register(
                    1L,
                    listOf(
                        OrderItemCommand.Register.Item(1L, 2),
                        OrderItemCommand.Register.Item(1L, 3),
                    ),
                )

            // when & then
            assertThrows<CoreException> {
                orderItemService.register(command)
            }
        }
    }

    @DisplayName("목록 조회")
    @Nested
    inner class FindAll {
        @Test
        fun `orderId로 주문 아이템들을 조회한다`() {
            // given
            orderItemRepository.saveAll(
                listOf(
                    OrderItem.create(1L, 1L, 2),
                    OrderItem.create(1L, 2L, 3),
                ),
            )

            // when
            val items = orderItemService.findAll(1L)

            // then
            assertThat(items).hasSize(2)
            assertThat(items[0].orderId).isEqualTo(1L)
            assertThat(items[1].orderId).isEqualTo(1L)
        }

        @Test
        fun `해당 orderId로 등록된 주문 아이템이 없으면 빈 리스트를 반환한다`() {
            // when
            val result = orderItemService.findAll(-1L)

            // then
            assertThat(result).isEmpty()
        }
    }
}
