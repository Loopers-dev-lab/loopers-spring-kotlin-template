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

@DisplayName("ProductEventMapper 테스트")
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
    @DisplayName("toLikeCommand 테스트")
    inner class ToLikeCommandTest {
        @Test
        @DisplayName("like.created 이벤트를 CREATED 타입으로 매핑한다")
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
        @DisplayName("like.canceled 이벤트를 CANCELED 타입으로 매핑한다")
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
        @DisplayName("혼합된 like 이벤트를 올바르게 매핑한다")
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
        @DisplayName("알 수 없는 like 이벤트 타입에서 예외가 발생한다")
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
        @DisplayName("빈 envelope 리스트에 대해 빈 커맨드를 반환한다")
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
    @DisplayName("toSalesCommand 테스트")
    inner class ToSalesCommandTest {
        @Test
        @DisplayName("여러 envelope의 orderItems를 평탄화한다")
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
        @DisplayName("빈 envelope 리스트에 대해 빈 커맨드를 반환한다")
        fun `returns empty command for empty envelopes`() {
            // given
            val envelopes = emptyList<CloudEventEnvelope>()

            // when
            val command = productEventMapper.toSalesCommand(envelopes)

            // then
            assertThat(command.items).isEmpty()
        }

        @Test
        @DisplayName("단일 주문의 여러 상품을 처리한다")
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
    @DisplayName("toViewCommand 테스트")
    inner class ToViewCommandTest {
        @Test
        @DisplayName("각 envelope을 item으로 매핑한다")
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
        @DisplayName("빈 envelope 리스트에 대해 빈 커맨드를 반환한다")
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
    @DisplayName("toStockDepletedProductIds 테스트")
    inner class ToStockDepletedProductIdsTest {
        @Test
        @DisplayName("envelope들에서 productId를 추출한다")
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
        @DisplayName("빈 envelope 리스트에 대해 빈 리스트를 반환한다")
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
    @DisplayName("toStockDepletedProductId 테스트")
    inner class ToStockDepletedProductIdTest {
        @Test
        @DisplayName("envelope에서 productId를 추출한다")
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
    @DisplayName("toLikeItem 테스트")
    inner class ToLikeItemTest {
        @Test
        @DisplayName("like.created 이벤트에서 CREATED 타입의 Item을 추출한다")
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
        @DisplayName("like.canceled 이벤트에서 CANCELED 타입의 Item을 추출한다")
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
        @DisplayName("알 수 없는 like 이벤트 타입에서 예외가 발생한다")
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
    @DisplayName("toSalesItems 테스트")
    inner class ToSalesItemsTest {
        @Test
        @DisplayName("여러 orderItems가 있는 주문 결제 이벤트에서 Item 리스트를 추출한다")
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
        @DisplayName("상품이 없는 주문에 대해 빈 리스트를 반환한다")
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
