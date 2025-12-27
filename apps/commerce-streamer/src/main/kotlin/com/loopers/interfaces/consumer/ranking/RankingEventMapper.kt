package com.loopers.interfaces.consumer.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.ranking.AccumulateLikeCanceledMetricCommand
import com.loopers.domain.ranking.AccumulateLikeCreatedMetricCommand
import com.loopers.domain.ranking.AccumulateOrderPaidMetricCommand
import com.loopers.domain.ranking.AccumulateViewMetricCommand
import com.loopers.domain.ranking.event.RankingLikeCanceledEventV1
import com.loopers.domain.ranking.event.RankingLikeCreatedEventV1
import com.loopers.domain.ranking.event.RankingOrderPaidEventV1
import com.loopers.domain.ranking.event.RankingProductViewedEventV1
import com.loopers.eventschema.CloudEventEnvelope
import org.springframework.stereotype.Component

/**
 * RankingEventMapper - CloudEventEnvelope를 Command 객체로 변환하는 매퍼
 *
 * - 역할: 이벤트 페이로드 파싱 및 Command 객체 생성
 * - 비즈니스 로직 없음: 순수 변환 작업만 수행
 * - ORDER_PAID 이벤트의 경우 각 상품별 unitPrice * quantity로 개별 금액 계산
 */
@Component
class RankingEventMapper(
    private val objectMapper: ObjectMapper,
) {
    /**
     * CloudEventEnvelope를 AccumulateViewMetricCommand로 변환
     *
     * - eventId는 envelope.id에서 추출 (멱등성 키에 사용)
     * - productId는 payload에서 추출
     * - occurredAt은 envelope.time에서 추출
     */
    fun toViewCommand(envelope: CloudEventEnvelope): AccumulateViewMetricCommand {
        val payload = objectMapper.readValue(envelope.payload, RankingProductViewedEventV1::class.java)
        return AccumulateViewMetricCommand(
            eventId = envelope.id,
            productId = payload.productId,
            occurredAt = envelope.time,
        )
    }

    /**
     * CloudEventEnvelope를 AccumulateLikeCreatedMetricCommand로 변환
     */
    fun toLikeCreatedCommand(envelope: CloudEventEnvelope): AccumulateLikeCreatedMetricCommand {
        val payload = objectMapper.readValue(envelope.payload, RankingLikeCreatedEventV1::class.java)
        return AccumulateLikeCreatedMetricCommand(
            eventId = envelope.id,
            productId = payload.productId,
            occurredAt = envelope.time,
        )
    }

    /**
     * CloudEventEnvelope를 AccumulateLikeCanceledMetricCommand로 변환
     */
    fun toLikeCanceledCommand(envelope: CloudEventEnvelope): AccumulateLikeCanceledMetricCommand {
        val payload = objectMapper.readValue(envelope.payload, RankingLikeCanceledEventV1::class.java)
        return AccumulateLikeCanceledMetricCommand(
            eventId = envelope.id,
            productId = payload.productId,
            occurredAt = envelope.time,
        )
    }

    /**
     * CloudEventEnvelope를 AccumulateOrderPaidMetricCommand로 변환
     *
     * - orderAmount는 각 상품별 unitPrice * quantity로 계산
     */
    fun toOrderPaidCommand(envelope: CloudEventEnvelope): AccumulateOrderPaidMetricCommand {
        val payload = objectMapper.readValue(envelope.payload, RankingOrderPaidEventV1::class.java)
        return AccumulateOrderPaidMetricCommand(
            eventId = envelope.id,
            items = payload.orderItems.map { item ->
                AccumulateOrderPaidMetricCommand.Item(
                    productId = item.productId,
                    orderAmount = (item.unitPrice * item.quantity).toBigDecimal(),
                )
            },
            occurredAt = envelope.time,
        )
    }
}
