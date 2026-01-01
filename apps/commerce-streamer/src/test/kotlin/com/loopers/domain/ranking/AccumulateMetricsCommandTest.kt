package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

@DisplayName("AccumulateMetricsCommand 단위 테스트")
class AccumulateMetricsCommandTest {

    private val testStatHour = Instant.parse("2024-01-15T01:00:00Z") // 2024-01-15 10:00 KST = 01:00 UTC

    @DisplayName("Item 생성 테스트")
    @Nested
    inner class ItemCreation {

        @DisplayName("모든 값을 명시적으로 지정하여 Item을 생성할 수 있다")
        @Test
        fun `can create Item with all explicit values`() {
            // given
            val productId = 123L
            val viewDelta = 10L
            val likeCreatedDelta = 5L
            val likeCanceledDelta = 2L
            val orderAmountDelta = BigDecimal("15000.50")

            // when
            val item = AccumulateMetricsCommand.Item(
                productId = productId,
                statHour = testStatHour,
                viewDelta = viewDelta,
                likeCreatedDelta = likeCreatedDelta,
                likeCanceledDelta = likeCanceledDelta,
                orderAmountDelta = orderAmountDelta,
            )

            // then
            assertThat(item.productId).isEqualTo(productId)
            assertThat(item.statHour).isEqualTo(testStatHour)
            assertThat(item.viewDelta).isEqualTo(viewDelta)
            assertThat(item.likeCreatedDelta).isEqualTo(likeCreatedDelta)
            assertThat(item.likeCanceledDelta).isEqualTo(likeCanceledDelta)
            assertThat(item.orderAmountDelta).isEqualByComparingTo(orderAmountDelta)
        }

        @DisplayName("기본값으로 Item을 생성하면 delta 값들이 0이다")
        @Test
        fun `Item created with defaults has zero delta values`() {
            // given
            val productId = 456L

            // when
            val item = AccumulateMetricsCommand.Item(
                productId = productId,
                statHour = testStatHour,
            )

            // then
            assertThat(item.productId).isEqualTo(productId)
            assertThat(item.statHour).isEqualTo(testStatHour)
            assertThat(item.viewDelta).isEqualTo(0L)
            assertThat(item.likeCreatedDelta).isEqualTo(0L)
            assertThat(item.likeCanceledDelta).isEqualTo(0L)
            assertThat(item.orderAmountDelta).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @DisplayName("viewDelta만 지정하여 Item을 생성할 수 있다")
        @Test
        fun `can create Item with only viewDelta specified`() {
            // given
            val productId = 789L
            val viewDelta = 100L

            // when
            val item = AccumulateMetricsCommand.Item(
                productId = productId,
                statHour = testStatHour,
                viewDelta = viewDelta,
            )

            // then
            assertThat(item.viewDelta).isEqualTo(viewDelta)
            assertThat(item.likeCreatedDelta).isEqualTo(0L)
            assertThat(item.likeCanceledDelta).isEqualTo(0L)
            assertThat(item.orderAmountDelta).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @DisplayName("orderAmountDelta만 지정하여 Item을 생성할 수 있다")
        @Test
        fun `can create Item with only orderAmountDelta specified`() {
            // given
            val productId = 321L
            val orderAmountDelta = BigDecimal("50000.00")

            // when
            val item = AccumulateMetricsCommand.Item(
                productId = productId,
                statHour = testStatHour,
                orderAmountDelta = orderAmountDelta,
            )

            // then
            assertThat(item.viewDelta).isEqualTo(0L)
            assertThat(item.likeCreatedDelta).isEqualTo(0L)
            assertThat(item.likeCanceledDelta).isEqualTo(0L)
            assertThat(item.orderAmountDelta).isEqualByComparingTo(orderAmountDelta)
        }
    }

    @DisplayName("Command 생성 테스트")
    @Nested
    inner class CommandCreation {

        @DisplayName("빈 리스트로 Command를 생성할 수 있다")
        @Test
        fun `can create Command with empty list`() {
            // when
            val command = AccumulateMetricsCommand(items = emptyList())

            // then
            assertThat(command.items).isEmpty()
        }

        @DisplayName("단일 Item으로 Command를 생성할 수 있다")
        @Test
        fun `can create Command with single Item`() {
            // given
            val item = AccumulateMetricsCommand.Item(
                productId = 1L,
                statHour = testStatHour,
                viewDelta = 5L,
            )

            // when
            val command = AccumulateMetricsCommand(items = listOf(item))

            // then
            assertThat(command.items).hasSize(1)
            assertThat(command.items[0]).isEqualTo(item)
        }

        @DisplayName("여러 Item으로 Command를 생성할 수 있다")
        @Test
        fun `can create Command with multiple Items`() {
            // given
            val items = listOf(
                AccumulateMetricsCommand.Item(
                    productId = 1L,
                    statHour = testStatHour,
                    viewDelta = 10L,
                ),
                AccumulateMetricsCommand.Item(
                    productId = 2L,
                    statHour = testStatHour,
                    likeCreatedDelta = 3L,
                ),
                AccumulateMetricsCommand.Item(
                    productId = 3L,
                    statHour = testStatHour,
                    orderAmountDelta = BigDecimal("30000.00"),
                ),
            )

            // when
            val command = AccumulateMetricsCommand(items = items)

            // then
            assertThat(command.items).hasSize(3)
            assertThat(command.items[0].productId).isEqualTo(1L)
            assertThat(command.items[0].viewDelta).isEqualTo(10L)
            assertThat(command.items[1].productId).isEqualTo(2L)
            assertThat(command.items[1].likeCreatedDelta).isEqualTo(3L)
            assertThat(command.items[2].productId).isEqualTo(3L)
            assertThat(command.items[2].orderAmountDelta).isEqualByComparingTo(BigDecimal("30000.00"))
        }

        @DisplayName("같은 productId를 가진 여러 Item으로 Command를 생성할 수 있다")
        @Test
        fun `can create Command with Items having same productId`() {
            // given
            val hour1 = testStatHour
            val hour2 = testStatHour.plusSeconds(3600)
            val items = listOf(
                AccumulateMetricsCommand.Item(
                    productId = 1L,
                    statHour = hour1,
                    viewDelta = 10L,
                ),
                AccumulateMetricsCommand.Item(
                    productId = 1L,
                    statHour = hour2,
                    viewDelta = 20L,
                ),
            )

            // when
            val command = AccumulateMetricsCommand(items = items)

            // then
            assertThat(command.items).hasSize(2)
            assertThat(command.items.filter { it.productId == 1L }).hasSize(2)
        }
    }

    @DisplayName("Data class 동등성 테스트")
    @Nested
    inner class Equality {

        @DisplayName("같은 값을 가진 Item은 동등하다")
        @Test
        fun `Items with same values are equal`() {
            // given
            val item1 = AccumulateMetricsCommand.Item(
                productId = 1L,
                statHour = testStatHour,
                viewDelta = 5L,
            )
            val item2 = AccumulateMetricsCommand.Item(
                productId = 1L,
                statHour = testStatHour,
                viewDelta = 5L,
            )

            // then
            assertThat(item1).isEqualTo(item2)
            assertThat(item1.hashCode()).isEqualTo(item2.hashCode())
        }

        @DisplayName("같은 Item 리스트를 가진 Command는 동등하다")
        @Test
        fun `Commands with same Items are equal`() {
            // given
            val items = listOf(
                AccumulateMetricsCommand.Item(
                    productId = 1L,
                    statHour = testStatHour,
                    viewDelta = 5L,
                ),
            )
            val command1 = AccumulateMetricsCommand(items = items)
            val command2 = AccumulateMetricsCommand(items = items)

            // then
            assertThat(command1).isEqualTo(command2)
            assertThat(command1.hashCode()).isEqualTo(command2.hashCode())
        }
    }
}
