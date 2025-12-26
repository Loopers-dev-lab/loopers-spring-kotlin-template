package com.loopers.domain.product

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ProductStatisticCommands 단위 테스트")
class ProductStatisticCommandsTest {

    @DisplayName("UpdateLikeCountCommand")
    @Nested
    inner class UpdateLikeCountCommandTest {

        @DisplayName("items 리스트로 커맨드를 생성할 수 있다")
        @Test
        fun `can create command with items list`() {
            // given
            val items = listOf(
                UpdateLikeCountCommand.Item(productId = 1L, type = UpdateLikeCountCommand.LikeType.CREATED),
                UpdateLikeCountCommand.Item(productId = 2L, type = UpdateLikeCountCommand.LikeType.CANCELED),
            )

            // when
            val command = UpdateLikeCountCommand(items = items)

            // then
            assertThat(command.items).hasSize(2)
            assertThat(command.items[0].productId).isEqualTo(1L)
            assertThat(command.items[0].type).isEqualTo(UpdateLikeCountCommand.LikeType.CREATED)
            assertThat(command.items[1].productId).isEqualTo(2L)
            assertThat(command.items[1].type).isEqualTo(UpdateLikeCountCommand.LikeType.CANCELED)
        }

        @DisplayName("빈 리스트로 커맨드를 생성할 수 있다")
        @Test
        fun `can create command with empty list`() {
            // given & when
            val command = UpdateLikeCountCommand(items = emptyList())

            // then
            assertThat(command.items).isEmpty()
        }

        @DisplayName("LikeType enum은 CREATED와 CANCELED 값을 가진다")
        @Test
        fun `LikeType enum has CREATED and CANCELED values`() {
            // given & when
            val values = UpdateLikeCountCommand.LikeType.entries

            // then
            assertThat(values).containsExactlyInAnyOrder(
                UpdateLikeCountCommand.LikeType.CREATED,
                UpdateLikeCountCommand.LikeType.CANCELED,
            )
        }

        @DisplayName("Item은 productId와 type을 가진다")
        @Test
        fun `Item has productId and type`() {
            // given & when
            val item = UpdateLikeCountCommand.Item(
                productId = 123L,
                type = UpdateLikeCountCommand.LikeType.CREATED,
            )

            // then
            assertThat(item.productId).isEqualTo(123L)
            assertThat(item.type).isEqualTo(UpdateLikeCountCommand.LikeType.CREATED)
        }
    }

    @DisplayName("UpdateSalesCountCommand")
    @Nested
    inner class UpdateSalesCountCommandTest {

        @DisplayName("items 리스트로 커맨드를 생성할 수 있다")
        @Test
        fun `can create command with items list`() {
            // given
            val items = listOf(
                UpdateSalesCountCommand.Item(productId = 1L, quantity = 3),
                UpdateSalesCountCommand.Item(productId = 2L, quantity = 5),
            )

            // when
            val command = UpdateSalesCountCommand(items = items)

            // then
            assertThat(command.items).hasSize(2)
            assertThat(command.items[0].productId).isEqualTo(1L)
            assertThat(command.items[0].quantity).isEqualTo(3)
            assertThat(command.items[1].productId).isEqualTo(2L)
            assertThat(command.items[1].quantity).isEqualTo(5)
        }

        @DisplayName("빈 리스트로 커맨드를 생성할 수 있다")
        @Test
        fun `can create command with empty list`() {
            // given & when
            val command = UpdateSalesCountCommand(items = emptyList())

            // then
            assertThat(command.items).isEmpty()
        }

        @DisplayName("Item은 productId와 quantity를 가진다")
        @Test
        fun `Item has productId and quantity`() {
            // given & when
            val item = UpdateSalesCountCommand.Item(
                productId = 456L,
                quantity = 10,
            )

            // then
            assertThat(item.productId).isEqualTo(456L)
            assertThat(item.quantity).isEqualTo(10)
        }
    }

    @DisplayName("UpdateViewCountCommand")
    @Nested
    inner class UpdateViewCountCommandTest {

        @DisplayName("items 리스트로 커맨드를 생성할 수 있다")
        @Test
        fun `can create command with items list`() {
            // given
            val items = listOf(
                UpdateViewCountCommand.Item(productId = 1L),
                UpdateViewCountCommand.Item(productId = 2L),
                UpdateViewCountCommand.Item(productId = 3L),
            )

            // when
            val command = UpdateViewCountCommand(items = items)

            // then
            assertThat(command.items).hasSize(3)
            assertThat(command.items[0].productId).isEqualTo(1L)
            assertThat(command.items[1].productId).isEqualTo(2L)
            assertThat(command.items[2].productId).isEqualTo(3L)
        }

        @DisplayName("빈 리스트로 커맨드를 생성할 수 있다")
        @Test
        fun `can create command with empty list`() {
            // given & when
            val command = UpdateViewCountCommand(items = emptyList())

            // then
            assertThat(command.items).isEmpty()
        }

        @DisplayName("Item은 productId를 가진다")
        @Test
        fun `Item has productId`() {
            // given & when
            val item = UpdateViewCountCommand.Item(productId = 789L)

            // then
            assertThat(item.productId).isEqualTo(789L)
        }
    }
}
