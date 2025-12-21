package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.domain.product.UpdateLikeCountCommand
import com.loopers.domain.product.UpdateSalesCountCommand
import com.loopers.domain.product.UpdateViewCountCommand
import com.loopers.eventschema.CloudEventEnvelope
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("ProductEventMapper")
class ProductEventMapperTest {
    private lateinit var objectMapper: ObjectMapper
    private lateinit var productEventMapper: ProductEventMapper

    @BeforeEach
    fun setUp() {
        objectMapper = jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
        productEventMapper = ProductEventMapper(objectMapper)
    }

    @Nested
    @DisplayName("toLikeCommand")
    inner class ToLikeCommandTest {
        @Test
        @DisplayName("maps like created events to CREATED type")
        fun `maps like created events to CREATED type`() {
            // given
            val envelopes = listOf(
                createEnvelope(
                    type = "loopers.like.created.v1",
                    aggregateId = "100",
                    payload = """{"productId": 100, "userId": 1}""",
                ),
                createEnvelope(
                    type = "loopers.like.created.v1",
                    aggregateId = "200",
                    payload = """{"productId": 200, "userId": 2}""",
                ),
            )

            // when
            val command = productEventMapper.toLikeCommand(envelopes)

            // then
            assertThat(command.items).hasSize(2)
            assertThat(command.items[0].productId).isEqualTo(100L)
            assertThat(command.items[0].type).isEqualTo(UpdateLikeCountCommand.LikeType.CREATED)
            assertThat(command.items[1].productId).isEqualTo(200L)
            assertThat(command.items[1].type).isEqualTo(UpdateLikeCountCommand.LikeType.CREATED)
        }

        @Test
        @DisplayName("maps like canceled events to CANCELED type")
        fun `maps like canceled events to CANCELED type`() {
            // given
            val envelopes = listOf(
                createEnvelope(
                    type = "loopers.like.canceled.v1",
                    aggregateId = "100",
                    payload = """{"productId": 100, "userId": 1}""",
                ),
            )

            // when
            val command = productEventMapper.toLikeCommand(envelopes)

            // then
            assertThat(command.items).hasSize(1)
            assertThat(command.items[0].productId).isEqualTo(100L)
            assertThat(command.items[0].type).isEqualTo(UpdateLikeCountCommand.LikeType.CANCELED)
        }

        @Test
        @DisplayName("maps mixed like events correctly")
        fun `maps mixed like events correctly`() {
            // given
            val envelopes = listOf(
                createEnvelope(
                    type = "loopers.like.created.v1",
                    aggregateId = "100",
                    payload = """{"productId": 100, "userId": 1}""",
                ),
                createEnvelope(
                    type = "loopers.like.canceled.v1",
                    aggregateId = "100",
                    payload = """{"productId": 100, "userId": 2}""",
                ),
                createEnvelope(
                    type = "loopers.like.created.v1",
                    aggregateId = "200",
                    payload = """{"productId": 200, "userId": 3}""",
                ),
            )

            // when
            val command = productEventMapper.toLikeCommand(envelopes)

            // then
            assertThat(command.items).hasSize(3)
            assertThat(command.items[0].type).isEqualTo(UpdateLikeCountCommand.LikeType.CREATED)
            assertThat(command.items[1].type).isEqualTo(UpdateLikeCountCommand.LikeType.CANCELED)
            assertThat(command.items[2].type).isEqualTo(UpdateLikeCountCommand.LikeType.CREATED)
        }

        @Test
        @DisplayName("throws on unknown like event type")
        fun `throws on unknown like event type`() {
            // given
            val envelopes = listOf(
                createEnvelope(
                    type = "loopers.like.unknown.v1",
                    aggregateId = "100",
                    payload = """{"productId": 100, "userId": 1}""",
                ),
            )

            // when & then
            assertThatThrownBy { productEventMapper.toLikeCommand(envelopes) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Unknown like event type")
                .hasMessageContaining("loopers.like.unknown.v1")
        }

        @Test
        @DisplayName("returns empty command for empty envelopes")
        fun `returns empty command for empty envelopes`() {
            // given
            val envelopes = emptyList<CloudEventEnvelope>()

            // when
            val command = productEventMapper.toLikeCommand(envelopes)

            // then
            assertThat(command.items).isEmpty()
        }
    }

    @Nested
    @DisplayName("toSalesCommand")
    inner class ToSalesCommandTest {
        @Test
        @DisplayName("flattens orderItems from multiple envelopes")
        fun `flattens orderItems from multiple envelopes`() {
            // given
            val envelopes = listOf(
                createEnvelope(
                    type = "loopers.order.paid.v1",
                    aggregateId = "order-1",
                    payload = """{"orderId": 1, "orderItems": [{"productId": 100, "quantity": 2}, {"productId": 200, "quantity": 3}]}""",
                ),
                createEnvelope(
                    type = "loopers.order.paid.v1",
                    aggregateId = "order-2",
                    payload = """{"orderId": 2, "orderItems": [{"productId": 300, "quantity": 1}]}""",
                ),
            )

            // when
            val command = productEventMapper.toSalesCommand(envelopes)

            // then
            assertThat(command.items).hasSize(3)
            assertThat(command.items[0]).isEqualTo(UpdateSalesCountCommand.Item(100L, 2))
            assertThat(command.items[1]).isEqualTo(UpdateSalesCountCommand.Item(200L, 3))
            assertThat(command.items[2]).isEqualTo(UpdateSalesCountCommand.Item(300L, 1))
        }

        @Test
        @DisplayName("returns empty command for empty envelopes")
        fun `returns empty command for empty envelopes`() {
            // given
            val envelopes = emptyList<CloudEventEnvelope>()

            // when
            val command = productEventMapper.toSalesCommand(envelopes)

            // then
            assertThat(command.items).isEmpty()
        }

        @Test
        @DisplayName("handles single order with multiple items")
        fun `handles single order with multiple items`() {
            // given
            val envelopes = listOf(
                createEnvelope(
                    type = "loopers.order.paid.v1",
                    aggregateId = "order-1",
                    payload = """{"orderId": 1, "orderItems": [{"productId": 100, "quantity": 5}, {"productId": 100, "quantity": 3}]}""",
                ),
            )

            // when
            val command = productEventMapper.toSalesCommand(envelopes)

            // then
            assertThat(command.items).hasSize(2)
            assertThat(command.items[0].productId).isEqualTo(100L)
            assertThat(command.items[0].quantity).isEqualTo(5)
            assertThat(command.items[1].productId).isEqualTo(100L)
            assertThat(command.items[1].quantity).isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("toViewCommand")
    inner class ToViewCommandTest {
        @Test
        @DisplayName("maps each envelope to item")
        fun `maps each envelope to item`() {
            // given
            val envelopes = listOf(
                createEnvelope(
                    type = "loopers.product.viewed.v1",
                    aggregateId = "100",
                    payload = """{"productId": 100, "userId": 1}""",
                ),
                createEnvelope(
                    type = "loopers.product.viewed.v1",
                    aggregateId = "200",
                    payload = """{"productId": 200, "userId": 2}""",
                ),
                createEnvelope(
                    type = "loopers.product.viewed.v1",
                    aggregateId = "100",
                    payload = """{"productId": 100, "userId": 3}""",
                ),
            )

            // when
            val command = productEventMapper.toViewCommand(envelopes)

            // then
            assertThat(command.items).hasSize(3)
            assertThat(command.items[0]).isEqualTo(UpdateViewCountCommand.Item(100L))
            assertThat(command.items[1]).isEqualTo(UpdateViewCountCommand.Item(200L))
            assertThat(command.items[2]).isEqualTo(UpdateViewCountCommand.Item(100L))
        }

        @Test
        @DisplayName("returns empty command for empty envelopes")
        fun `returns empty command for empty envelopes`() {
            // given
            val envelopes = emptyList<CloudEventEnvelope>()

            // when
            val command = productEventMapper.toViewCommand(envelopes)

            // then
            assertThat(command.items).isEmpty()
        }
    }

    @Nested
    @DisplayName("toStockDepletedProductIds")
    inner class ToStockDepletedProductIdsTest {
        @Test
        @DisplayName("extracts productIds from envelopes")
        fun `extracts productIds from envelopes`() {
            // given
            val envelopes = listOf(
                createEnvelope(
                    type = "loopers.stock.depleted.v1",
                    aggregateId = "100",
                    payload = """{"productId": 100}""",
                ),
                createEnvelope(
                    type = "loopers.stock.depleted.v1",
                    aggregateId = "200",
                    payload = """{"productId": 200}""",
                ),
                createEnvelope(
                    type = "loopers.stock.depleted.v1",
                    aggregateId = "300",
                    payload = """{"productId": 300}""",
                ),
            )

            // when
            val productIds = productEventMapper.toStockDepletedProductIds(envelopes)

            // then
            assertThat(productIds).containsExactly(100L, 200L, 300L)
        }

        @Test
        @DisplayName("returns empty list for empty envelopes")
        fun `returns empty list for empty envelopes`() {
            // given
            val envelopes = emptyList<CloudEventEnvelope>()

            // when
            val productIds = productEventMapper.toStockDepletedProductIds(envelopes)

            // then
            assertThat(productIds).isEmpty()
        }
    }

    @Nested
    @DisplayName("toStockDepletedProductId")
    inner class ToStockDepletedProductIdTest {
        @Test
        @DisplayName("extracts productId from envelope")
        fun `extracts productId from envelope`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.stock.depleted.v1",
                aggregateId = "100",
                payload = """{"productId": 100}""",
            )

            // when
            val productId = productEventMapper.toStockDepletedProductId(envelope)

            // then
            assertThat(productId).isEqualTo(100L)
        }
    }

    @Nested
    @DisplayName("toLikeItem")
    inner class ToLikeItemTest {
        @Test
        @DisplayName("extracts Item with CREATED type for like.created event")
        fun `extracts Item with CREATED type for like created event`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.like.created.v1",
                aggregateId = "100",
                payload = """{"productId": 100, "userId": 1}""",
            )

            // when
            val item = productEventMapper.toLikeItem(envelope)

            // then
            assertThat(item.productId).isEqualTo(100L)
            assertThat(item.type).isEqualTo(UpdateLikeCountCommand.LikeType.CREATED)
        }

        @Test
        @DisplayName("extracts Item with CANCELED type for like.canceled event")
        fun `extracts Item with CANCELED type for like canceled event`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.like.canceled.v1",
                aggregateId = "200",
                payload = """{"productId": 200, "userId": 2}""",
            )

            // when
            val item = productEventMapper.toLikeItem(envelope)

            // then
            assertThat(item.productId).isEqualTo(200L)
            assertThat(item.type).isEqualTo(UpdateLikeCountCommand.LikeType.CANCELED)
        }

        @Test
        @DisplayName("throws on unknown like event type")
        fun `throws on unknown like event type`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.like.unknown.v1",
                aggregateId = "100",
                payload = """{"productId": 100, "userId": 1}""",
            )

            // when & then
            assertThatThrownBy { productEventMapper.toLikeItem(envelope) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Unknown like event type")
                .hasMessageContaining("loopers.like.unknown.v1")
        }
    }

    @Nested
    @DisplayName("toSalesItems")
    inner class ToSalesItemsTest {
        @Test
        @DisplayName("extracts list of Items from order paid event with multiple orderItems")
        fun `extracts list of Items from order paid event with multiple orderItems`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.order.paid.v1",
                aggregateId = "order-1",
                payload = """{"orderId": 1, "orderItems": [{"productId": 100, "quantity": 2}, {"productId": 200, "quantity": 3}]}""",
            )

            // when
            val items = productEventMapper.toSalesItems(envelope)

            // then
            assertThat(items).hasSize(2)
            assertThat(items[0]).isEqualTo(UpdateSalesCountCommand.Item(100L, 2))
            assertThat(items[1]).isEqualTo(UpdateSalesCountCommand.Item(200L, 3))
        }

        @Test
        @DisplayName("returns empty list for order with no items")
        fun `returns empty list for order with no items`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.order.paid.v1",
                aggregateId = "order-1",
                payload = """{"orderId": 1, "orderItems": []}""",
            )

            // when
            val items = productEventMapper.toSalesItems(envelope)

            // then
            assertThat(items).isEmpty()
        }
    }

    private fun createEnvelope(
        id: String = "evt-${System.nanoTime()}",
        type: String,
        aggregateId: String,
        payload: String,
    ): CloudEventEnvelope =
        CloudEventEnvelope(
            id = id,
            type = type,
            source = "commerce-api",
            aggregateType = "Product",
            aggregateId = aggregateId,
            time = Instant.now(),
            payload = payload,
        )
}
