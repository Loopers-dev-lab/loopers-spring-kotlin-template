package com.loopers.interfaces.scheduler

import com.loopers.domain.ranking.RankingAggregationService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * RankingFlushScheduler - 랭킹 집계 버퍼 플러시 스케줄러
 *
 * 30초마다 RankingAggregationService.flush()를 호출하여
 * 메모리 버퍼에 쌓인 이벤트들을 Redis와 DB에 영속화합니다.
 */
@Component
@ConditionalOnProperty(
    name = ["scheduler.ranking-flush.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class RankingFlushScheduler(
    private val rankingAggregationService: RankingAggregationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 30000)
    fun flush() {
        log.debug("[RankingFlushScheduler] triggering flush")
        rankingAggregationService.flush()
    }
}
