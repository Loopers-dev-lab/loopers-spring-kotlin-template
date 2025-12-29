package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 랭킹 콜드 스타트 방지 Scheduler
 */
@Component
class RankingColdStartScheduler(
    private val rankingService: RankingService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val CARRY_OVER_WEIGHT = 0.1 // 10% 가중치
    }

    /**
     * 매일 23:50에 다음 날 랭킹 초기화
     */
    @Scheduled(cron = "0 50 23 * * *")
    fun carryOverDailyScores() {
        try {
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)

            logger.info("랭킹 Score Carry-Over 시작: $today → $tomorrow")

            // 오늘 점수의 10%를 내일 키로 복사
            val copiedCount = rankingService.carryOverScores(today, tomorrow, CARRY_OVER_WEIGHT)

            if (copiedCount > 0) {
                logger.info(
                    "랭킹 Score Carry-Over 성공: " +
                            "${copiedCount}개 상품 복사 ($today → $tomorrow, weight=$CARRY_OVER_WEIGHT)"
                )
            } else {
                logger.warn(
                    "랭킹 Score Carry-Over: copiedCount=0 " +
                    "(원본 키가 비어있거나 대상 키가 이미 존재함)"
                )
            }
        } catch (e: Exception) {
            logger.error("랭킹 Score Carry-Over 실패: ${e.message}", e)
        }
    }

    /**
     * 테스트용: 수동 실행 메서드
     */
    fun executeNow() {
        logger.info("수동 실행: 랭킹 Score Carry-Over")
        carryOverDailyScores()
    }
}
