package com.loopers.interfaces.consumer.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.ranking.RankingEvent
import com.loopers.domain.ranking.RankingEventType
import com.loopers.domain.ranking.event.RankingLikeCanceledEventV1
import com.loopers.domain.ranking.event.RankingLikeCreatedEventV1
import com.loopers.domain.ranking.event.RankingOrderPaidEventV1
import com.loopers.domain.ranking.event.RankingProductViewedEventV1
import com.loopers.eventschema.CloudEventEnvelope
import org.springframework.stereotype.Component
import java.math.RoundingMode

/**
 * RankingEventMapper - CloudEventEnvelope를 RankingEvent로 변환하는 매퍼
 *
 * - 역할: 이벤트 페이로드 파싱 및 RankingEvent 객체 생성
 * - 비즈니스 로직 없음: 순수 변환 작업만 수행
 * - ORDER_PAID 이벤트의 경우 totalAmount를 orderItems 수로 균등 분배
 */
@Component
class RankingEventMapper(
    private val objectMapper: ObjectMapper,
) {
    /**
     * CloudEventEnvelope를 RankingEvent 목록으로 변환
     *
     * - VIEW, LIKE_CREATED, LIKE_CANCELED: 단일 RankingEvent 반환
     * - ORDER_PAID: 각 orderItem별로 RankingEvent 생성 (totalAmount 균등 분배)
     *
     * @param envelope CloudEventEnvelope
     * @return RankingEvent 목록
     * @throws IllegalArgumentException 알 수 없는 이벤트 타입인 경우
     */
    fun toRankingEvents(envelope: CloudEventEnvelope): List<RankingEvent> {
        return when (envelope.type) {
            "loopers.product.viewed.v1" -> listOf(mapProductViewedEvent(envelope))
            "loopers.like.created.v1" -> listOf(mapLikeEvent(envelope, RankingEventType.LIKE_CREATED))
            "loopers.like.canceled.v1" -> listOf(mapLikeEvent(envelope, RankingEventType.LIKE_CANCELED))
            "loopers.order.paid.v1" -> mapOrderPaidEvent(envelope)
            else -> throw IllegalArgumentException("Unknown event type: ${envelope.type}")
        }
    }

    private fun mapProductViewedEvent(envelope: CloudEventEnvelope): RankingEvent {
        val payload = objectMapper.readValue(envelope.payload, RankingProductViewedEventV1::class.java)
        return RankingEvent(
            productId = payload.productId,
            eventType = RankingEventType.VIEW,
            orderAmount = null,
            occurredAt = envelope.time,
        )
    }

    private fun mapLikeEvent(envelope: CloudEventEnvelope, eventType: RankingEventType): RankingEvent {
        val productId = when (eventType) {
            RankingEventType.LIKE_CREATED ->
                objectMapper.readValue(envelope.payload, RankingLikeCreatedEventV1::class.java).productId
            RankingEventType.LIKE_CANCELED ->
                objectMapper.readValue(envelope.payload, RankingLikeCanceledEventV1::class.java).productId
            else -> throw IllegalArgumentException("Unexpected event type for like event: $eventType")
        }
        return RankingEvent(
            productId = productId,
            eventType = eventType,
            orderAmount = null,
            occurredAt = envelope.time,
        )
    }

    private fun mapOrderPaidEvent(envelope: CloudEventEnvelope): List<RankingEvent> {
        val payload = objectMapper.readValue(envelope.payload, RankingOrderPaidEventV1::class.java)

        if (payload.orderItems.isEmpty()) {
            return emptyList()
        }

        val amountPerItem = payload.totalAmount.toBigDecimal()
            .divide(payload.orderItems.size.toBigDecimal(), 2, RoundingMode.HALF_UP)

        return payload.orderItems.map { item ->
            RankingEvent(
                productId = item.productId,
                eventType = RankingEventType.ORDER_PAID,
                orderAmount = amountPerItem,
                occurredAt = envelope.time,
            )
        }
    }
}
