package com.loopers.interfaces.consumer.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.ranking.AccumulateMetricCommand
import com.loopers.domain.ranking.MetricType
import com.loopers.domain.ranking.event.RankingLikeCanceledEventV1
import com.loopers.domain.ranking.event.RankingLikeCreatedEventV1
import com.loopers.domain.ranking.event.RankingOrderPaidEventV1
import com.loopers.domain.ranking.event.RankingProductViewedEventV1
import com.loopers.eventschema.CloudEventEnvelope
import org.springframework.stereotype.Component

/**
 * RankingEventMapper - CloudEventEnvelope를 AccumulateMetricCommand.Item으로 변환하는 매퍼
 *
 * - 역할: 이벤트 페이로드 파싱 및 AccumulateMetricCommand.Item 객체 생성
 * - 비즈니스 로직 없음: 순수 변환 작업만 수행
 * - ORDER_PAID 이벤트의 경우 각 상품별 unitPrice * quantity로 개별 금액 계산
 */
@Component
class RankingEventMapper(
    private val objectMapper: ObjectMapper,
) {
    /**
     * CloudEventEnvelope를 AccumulateMetricCommand.Item 목록으로 변환
     *
     * - VIEW, LIKE_CREATED, LIKE_CANCELED: 단일 Item 반환
     * - ORDER_PAID: 각 orderItem별로 Item 생성 (unitPrice * quantity로 개별 계산)
     *
     * @param envelope CloudEventEnvelope
     * @return AccumulateMetricCommand.Item 목록
     * @throws IllegalArgumentException 알 수 없는 이벤트 타입인 경우
     */
    fun toAccumulateMetricItems(envelope: CloudEventEnvelope): List<AccumulateMetricCommand.Item> {
        return when (envelope.type) {
            "loopers.product.viewed.v1" -> listOf(mapProductViewedEvent(envelope))
            "loopers.like.created.v1" -> listOf(mapLikeEvent(envelope, MetricType.LIKE_CREATED))
            "loopers.like.canceled.v1" -> listOf(mapLikeEvent(envelope, MetricType.LIKE_CANCELED))
            "loopers.order.paid.v1" -> mapOrderPaidEvent(envelope)
            else -> throw IllegalArgumentException("Unknown event type: ${envelope.type}")
        }
    }

    private fun mapProductViewedEvent(envelope: CloudEventEnvelope): AccumulateMetricCommand.Item {
        val payload = objectMapper.readValue(envelope.payload, RankingProductViewedEventV1::class.java)
        return AccumulateMetricCommand.Item(
            productId = payload.productId,
            metricType = MetricType.VIEW,
            orderAmount = null,
            occurredAt = envelope.time,
        )
    }

    private fun mapLikeEvent(envelope: CloudEventEnvelope, metricType: MetricType): AccumulateMetricCommand.Item {
        val productId = when (metricType) {
            MetricType.LIKE_CREATED ->
                objectMapper.readValue(envelope.payload, RankingLikeCreatedEventV1::class.java).productId
            MetricType.LIKE_CANCELED ->
                objectMapper.readValue(envelope.payload, RankingLikeCanceledEventV1::class.java).productId
            else -> throw IllegalArgumentException("Unexpected metric type for like event: $metricType")
        }
        return AccumulateMetricCommand.Item(
            productId = productId,
            metricType = metricType,
            orderAmount = null,
            occurredAt = envelope.time,
        )
    }

    private fun mapOrderPaidEvent(envelope: CloudEventEnvelope): List<AccumulateMetricCommand.Item> {
        val payload = objectMapper.readValue(envelope.payload, RankingOrderPaidEventV1::class.java)

        if (payload.orderItems.isEmpty()) {
            return emptyList()
        }

        return payload.orderItems.map { item ->
            AccumulateMetricCommand.Item(
                productId = item.productId,
                metricType = MetricType.ORDER_PAID,
                orderAmount = (item.unitPrice * item.quantity).toBigDecimal(),
                occurredAt = envelope.time,
            )
        }
    }
}
