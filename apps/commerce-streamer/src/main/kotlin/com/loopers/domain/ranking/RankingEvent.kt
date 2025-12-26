package com.loopers.domain.ranking

import java.math.BigDecimal
import java.time.Instant

/**
 * RankingEvent - 랭킹 집계를 위한 내부 이벤트 표현
 *
 * - Kafka에서 수신한 CloudEventEnvelope를 변환한 도메인 내부 표현
 * - 버퍼에 적재되어 flush 시 점수 계산에 사용
 */
data class RankingEvent(
    val productId: Long,
    val eventType: RankingEventType,
    val orderAmount: BigDecimal?,
    val occurredAt: Instant,
)
