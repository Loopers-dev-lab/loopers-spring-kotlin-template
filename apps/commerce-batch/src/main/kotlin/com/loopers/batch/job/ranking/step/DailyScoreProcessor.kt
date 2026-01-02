package com.loopers.batch.job.ranking.step

import com.loopers.domain.ranking.ProductDailyMetric
import com.loopers.domain.ranking.RankingScoreCalculator
import com.loopers.domain.ranking.RankingWeight
import org.springframework.batch.item.ItemProcessor
import java.time.LocalDate

/**
 * DailyScoreProcessor - 일별 메트릭에서 인기 점수 계산
 *
 * - RankingScoreCalculator.calculateSingleDaily()에 위임
 * - 감쇠 공식: baseDate와 같은 날짜는 0.9, 이전 날짜는 0.1 가중치 적용
 *
 * @param calculator 랭킹 점수 계산기
 * @param baseDate 기준 날짜 (오늘)
 * @param weight 가중치 설정
 */
class DailyScoreProcessor(
    private val calculator: RankingScoreCalculator,
    private val baseDate: LocalDate,
    private val weight: RankingWeight,
) : ItemProcessor<ProductDailyMetric, ScoreEntry> {

    override fun process(item: ProductDailyMetric): ScoreEntry {
        return calculator.calculateSingleDaily(item, baseDate, weight)
    }
}
