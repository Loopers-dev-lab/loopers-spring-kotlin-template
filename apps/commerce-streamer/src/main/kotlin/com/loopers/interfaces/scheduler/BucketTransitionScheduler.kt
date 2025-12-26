package com.loopers.interfaces.scheduler

import com.loopers.domain.ranking.RankingAggregationService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * BucketTransitionScheduler - 시간별 버킷 전환 스케줄러
 *
 * 매 시간 59분에 RankingAggregationService.transitionBucket()을 호출하여
 * 이전 시간 버킷의 점수에 감쇠(0.1)를 적용한 새 버킷을 생성합니다.
 *
 * :59분에 실행하는 이유:
 * - 현재 시간대의 이벤트를 최대한 많이 수집한 후 전환
 * - 정각에 실행하면 현재 시간대의 초기 이벤트가 누락될 수 있음
 */
@Component
@ConditionalOnProperty(
    name = ["scheduler.bucket-transition.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class BucketTransitionScheduler(
    private val rankingAggregationService: RankingAggregationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 59 * * * *")
    fun transition() {
        log.debug("[BucketTransitionScheduler] triggering bucket transition")
        rankingAggregationService.transitionBucket()
    }
}
