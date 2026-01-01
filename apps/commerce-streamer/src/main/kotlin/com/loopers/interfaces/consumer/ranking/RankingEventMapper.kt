package com.loopers.interfaces.consumer.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.ranking.AccumulateMetricsCommand
import com.loopers.domain.ranking.event.RankingLikeCanceledEventV1
import com.loopers.domain.ranking.event.RankingLikeCreatedEventV1
import com.loopers.domain.ranking.event.RankingOrderPaidEventV1
import com.loopers.domain.ranking.event.RankingProductViewedEventV1
import com.loopers.eventschema.CloudEventEnvelope
import org.springframework.stereotype.Component
import java.time.temporal.ChronoUnit

/**
 * RankingEventMapper - CloudEventEnvelope를 AccumulateMetricsCommand.Item으로 변환하는 매퍼
 *
 * - 역할: 이벤트 페이로드 파싱 및 배치 커맨드 아이템 생성
 * - 비즈니스 로직 없음: 순수 변환 작업만 수행
 * - ORDER_PAID 이벤트의 경우 각 상품별로 별도의 Item을 생성
 */
@Component
class RankingEventMapper(
    private val objectMapper: ObjectMapper,
) {
    /**
     * CloudEventEnvelope를 AccumulateMetricsCommand.Item 리스트로 변환
     *
     * - VIEW, LIKE_CREATED, LIKE_CANCELED: 단일 Item 반환
     * - ORDER_PAID: 주문 아이템 개수만큼 Item 반환 (상품별로 분리)
     *
     * @return 변환된 Item 리스트 (지원하지 않는 타입이면 빈 리스트)
     */
    fun toCommandItems(envelope: CloudEventEnvelope): List<AccumulateMetricsCommand.Item> {
        val statHour = envelope.time.truncatedTo(ChronoUnit.HOURS)

        return when (envelope.type) {
            "loopers.product.viewed.v1" -> {
                val payload = objectMapper.readValue(envelope.payload, RankingProductViewedEventV1::class.java)
                listOf(
                    AccumulateMetricsCommand.Item(
                        productId = payload.productId,
                        statHour = statHour,
                        viewDelta = 1,
                    ),
                )
            }
            "loopers.like.created.v1" -> {
                val payload = objectMapper.readValue(envelope.payload, RankingLikeCreatedEventV1::class.java)
                listOf(
                    AccumulateMetricsCommand.Item(
                        productId = payload.productId,
                        statHour = statHour,
                        likeCreatedDelta = 1,
                    ),
                )
            }
            "loopers.like.canceled.v1" -> {
                val payload = objectMapper.readValue(envelope.payload, RankingLikeCanceledEventV1::class.java)
                listOf(
                    AccumulateMetricsCommand.Item(
                        productId = payload.productId,
                        statHour = statHour,
                        likeCanceledDelta = 1,
                    ),
                )
            }
            "loopers.order.paid.v1" -> {
                val payload = objectMapper.readValue(envelope.payload, RankingOrderPaidEventV1::class.java)
                payload.orderItems.map { item ->
                    AccumulateMetricsCommand.Item(
                        productId = item.productId,
                        statHour = statHour,
                        orderAmountDelta = (item.unitPrice * item.quantity).toBigDecimal(),
                    )
                }
            }
            else -> emptyList()
        }
    }
}
