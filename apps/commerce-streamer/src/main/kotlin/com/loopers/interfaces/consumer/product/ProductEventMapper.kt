package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.product.UpdateLikeCountCommand
import com.loopers.domain.product.UpdateSalesCountCommand
import com.loopers.domain.product.UpdateViewCountCommand
import com.loopers.domain.product.event.LikeEvent
import com.loopers.domain.product.event.OrderPaidEvent
import com.loopers.domain.product.event.ProductViewedEvent
import com.loopers.domain.product.event.StockDepletedEvent
import com.loopers.eventschema.CloudEventEnvelope
import org.springframework.stereotype.Component

/**
 * ProductEventMapper - CloudEventEnvelope을 도메인 Command로 변환하는 매퍼
 *
 * - 역할: 이벤트 페이로드 파싱 및 Command 객체 생성
 * - 비즈니스 로직 없음: 순수 변환 작업만 수행
 */
@Component
class ProductEventMapper(
    private val objectMapper: ObjectMapper,
) {
    /**
     * Like 이벤트 목록을 UpdateLikeCountCommand로 변환
     *
     * @param envelopes Like 관련 이벤트 envelope 목록 (created/canceled)
     * @return UpdateLikeCountCommand - 각 이벤트의 productId와 LikeType 정보 포함
     * @throws IllegalArgumentException 알 수 없는 like 이벤트 타입인 경우
     */
    fun toLikeCommand(envelopes: List<CloudEventEnvelope>): UpdateLikeCountCommand {
        val items = envelopes.map { envelope ->
            val payload = objectMapper.readValue(envelope.payload, LikeEvent::class.java)
            val type = when (envelope.type) {
                "loopers.like.created.v1" -> UpdateLikeCountCommand.LikeType.CREATED
                "loopers.like.canceled.v1" -> UpdateLikeCountCommand.LikeType.CANCELED
                else -> throw IllegalArgumentException("Unknown like event type: ${envelope.type}")
            }
            UpdateLikeCountCommand.Item(payload.productId, type)
        }
        return UpdateLikeCountCommand(items)
    }

    /**
     * Order Paid 이벤트 목록을 UpdateSalesCountCommand로 변환
     *
     * @param envelopes Order Paid 이벤트 envelope 목록
     * @return UpdateSalesCountCommand - 모든 주문의 orderItems를 평탄화하여 포함
     */
    fun toSalesCommand(envelopes: List<CloudEventEnvelope>): UpdateSalesCountCommand {
        val items = envelopes.flatMap { envelope ->
            val payload = objectMapper.readValue(envelope.payload, OrderPaidEvent::class.java)
            payload.orderItems.map { UpdateSalesCountCommand.Item(it.productId, it.quantity) }
        }
        return UpdateSalesCountCommand(items)
    }

    /**
     * Product Viewed 이벤트 목록을 UpdateViewCountCommand로 변환
     *
     * @param envelopes Product Viewed 이벤트 envelope 목록
     * @return UpdateViewCountCommand - 각 이벤트의 productId 정보 포함
     */
    fun toViewCommand(envelopes: List<CloudEventEnvelope>): UpdateViewCountCommand {
        val items = envelopes.map { envelope ->
            val payload = objectMapper.readValue(envelope.payload, ProductViewedEvent::class.java)
            UpdateViewCountCommand.Item(payload.productId)
        }
        return UpdateViewCountCommand(items)
    }

    /**
     * Stock Depleted 이벤트 목록에서 productId 목록 추출
     *
     * @param envelopes Stock Depleted 이벤트 envelope 목록
     * @return productId 목록 - 캐시 무효화 대상
     */
    fun toStockDepletedProductIds(envelopes: List<CloudEventEnvelope>): List<Long> =
        envelopes.map { envelope -> toStockDepletedProductId(envelope) }

    /**
     * 단일 Stock Depleted 이벤트에서 productId 추출
     *
     * @param envelope Stock Depleted 이벤트 envelope
     * @return productId - 캐시 무효화 대상
     */
    fun toStockDepletedProductId(envelope: CloudEventEnvelope): Long {
        val payload = objectMapper.readValue(envelope.payload, StockDepletedEvent::class.java)
        return payload.productId
    }

    /**
     * 단일 Like 이벤트를 UpdateLikeCountCommand.Item으로 변환
     *
     * @param envelope Like 관련 이벤트 envelope (created/canceled)
     * @return UpdateLikeCountCommand.Item - productId와 LikeType 정보 포함
     * @throws IllegalArgumentException 알 수 없는 like 이벤트 타입인 경우
     */
    fun toLikeItem(envelope: CloudEventEnvelope): UpdateLikeCountCommand.Item {
        val payload = objectMapper.readValue(envelope.payload, LikeEvent::class.java)
        val type = when (envelope.type) {
            "loopers.like.created.v1" -> UpdateLikeCountCommand.LikeType.CREATED
            "loopers.like.canceled.v1" -> UpdateLikeCountCommand.LikeType.CANCELED
            else -> throw IllegalArgumentException("Unknown like event type: ${envelope.type}")
        }
        return UpdateLikeCountCommand.Item(payload.productId, type)
    }

    /**
     * 단일 Order Paid 이벤트를 UpdateSalesCountCommand.Item 목록으로 변환
     *
     * @param envelope Order Paid 이벤트 envelope
     * @return UpdateSalesCountCommand.Item 목록 - 해당 주문의 orderItems 정보 포함
     */
    fun toSalesItems(envelope: CloudEventEnvelope): List<UpdateSalesCountCommand.Item> {
        val payload = objectMapper.readValue(envelope.payload, OrderPaidEvent::class.java)
        return payload.orderItems.map { UpdateSalesCountCommand.Item(it.productId, it.quantity) }
    }
}
