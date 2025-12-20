package com.loopers.interfaces.scheduler

import com.loopers.domain.ranking.RankingService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 콜드 스타트 완화를 위한 랭킹 점수 이월 스케줄러
 */
@Component
class RankingCarryOverScheduler(
    private val rankingService: RankingService,
) {
    private val log = LoggerFactory.getLogger(RankingCarryOverScheduler::class.java)

    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    fun carryOverDailyScores() {
        log.info("랭킹 점수 이월 스케줄 시작")
        rankingService.carryOverDailyScores()
    }
}
