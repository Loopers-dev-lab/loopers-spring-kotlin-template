package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingKey
import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingScope
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 랭킹 스케줄러
 *
 * 콜드 스타트 방지를 위한 Score Carry-Over 처리
 */
@Component
class RankingScheduler(private val rankingRepository: RankingRepository) {
    private val logger = LoggerFactory.getLogger(RankingScheduler::class.java)

    /**
     * 일간 랭킹 콜드 스타트 방지
     *
     * 매일 23시 50분에 실행:
     * - 오늘의 랭킹 데이터를 10% 가중치로 내일 랭킹에 미리 복사
     * - 내일 0시부터 랭킹 데이터가 존재하도록 보장 (콜드 스타트 방지)
     *
     * Cron: 초 분 시 일 월 요일
     * "0 50 23 * * *" = 매일 23시 50분 (Asia/Seoul)
     */
    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    fun carryOverDailyRanking() {
        try {
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)

            val todayKey = RankingKey.daily(RankingScope.ALL, today)
            val tomorrowKey = RankingKey.daily(RankingScope.ALL, tomorrow)

            // 오늘 랭킹이 존재하는지 확인
            val todayCount = rankingRepository.getCount(todayKey)
            if (todayCount == 0L) {
                logger.warn(
                    "일간 랭킹 콜드 스타트 방지: 오늘 랭킹 데이터가 없어 복사하지 않음 - " +
                            "today=$today, count=0",
                )
                return
            }

            // 10% 가중치로 내일 랭킹에 복사
            val carryOverWeight = 0.1
            rankingRepository.copyWithWeight(todayKey, tomorrowKey, carryOverWeight)

            // 내일 랭킹에 TTL 설정
            rankingRepository.setExpire(tomorrowKey)

            logger.info(
                "일간 랭킹 콜드 스타트 방지 완료: " +
                        "source=$today, target=$tomorrow, weight=$carryOverWeight, count=$todayCount",
            )
        } catch (e: Exception) {
            logger.error("일간 랭킹 콜드 스타트 방지 실패", e)
        }
    }

    /**
     * 시간별 랭킹 콜드 스타트 방지
     *
     * 매시간 50분에 실행:
     * - 현재 시간 랭킹 데이터를 10% 가중치로 다음 시간 랭킹에 미리 복사
     *
     * Cron: "0 50 * * * *" = 매시간 50분 (Asia/Seoul)
     */
    @Scheduled(cron = "0 50 * * * *", zone = "Asia/Seoul")
    fun carryOverHourlyRanking() {
        try {
            val now = java.time.LocalDateTime.now()
            val currentHour = now.withMinute(0).withSecond(0).withNano(0)
            val nextHour = currentHour.plusHours(1)

            val currentKey = RankingKey.hourly(RankingScope.ALL, currentHour)
            val nextKey = RankingKey.hourly(RankingScope.ALL, nextHour)

            // 현재 시간 랭킹이 존재하는지 확인
            val currentCount = rankingRepository.getCount(currentKey)
            if (currentCount == 0L) {
                logger.warn(
                    "시간별 랭킹 콜드 스타트 방지: 현재 시간 랭킹 데이터가 없어 복사하지 않음 - " +
                            "currentHour=$currentHour, count=0",
                )
                return
            }

            // 10% 가중치로 다음 시간 랭킹에 복사
            val carryOverWeight = 0.1
            rankingRepository.copyWithWeight(currentKey, nextKey, carryOverWeight)

            // 다음 시간 랭킹에 TTL 설정
            rankingRepository.setExpire(nextKey)

            logger.info(
                "시간별 랭킹 콜드 스타트 방지 완료: " +
                        "source=$currentHour, target=$nextHour, weight=$carryOverWeight, count=$currentCount",
            )
        } catch (e: Exception) {
            logger.error("시간별 랭킹 콜드 스타트 방지 실패", e)
        }
    }
}
