package com.loopers.domain.ranking

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * AggregationKey - 버퍼 집계를 위한 키
 *
 * - productId와 hourBucket 조합으로 집계 단위를 결정
 * - hourBucket은 이벤트 발생 시각(occurredAt)을 시간 단위로 truncate한 값
 * - 14:59:58에 발생한 이벤트가 15:00:02에 flush되어도 14:00 버킷에 올바르게 집계됨
 */
data class AggregationKey(
    val productId: Long,
    val hourBucket: Instant,
) {
    companion object {
        /**
         * 이벤트로부터 AggregationKey 생성
         * hourBucket은 occurredAt을 시간 단위로 truncate
         */
        fun from(event: RankingEvent): AggregationKey {
            return AggregationKey(
                productId = event.productId,
                hourBucket = event.occurredAt.truncatedTo(ChronoUnit.HOURS),
            )
        }

        /**
         * productId와 발생 시각으로 AggregationKey 생성
         */
        fun of(productId: Long, occurredAt: Instant): AggregationKey {
            return AggregationKey(
                productId = productId,
                hourBucket = occurredAt.truncatedTo(ChronoUnit.HOURS),
            )
        }
    }
}
