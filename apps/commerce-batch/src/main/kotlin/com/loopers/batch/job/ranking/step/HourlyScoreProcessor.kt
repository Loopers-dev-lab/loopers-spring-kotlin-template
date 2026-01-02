package com.loopers.batch.job.ranking.step

import com.loopers.domain.ranking.ProductHourlyMetric
import com.loopers.domain.ranking.RankingScoreCalculator
import com.loopers.domain.ranking.RankingWeight
import org.springframework.batch.item.ItemProcessor
import java.time.Instant

/**
 * HourlyScoreProcessor - 시간별 메트릭에서 인기 점수 계산
 *
 * - RankingScoreCalculator.calculateSingleHourly()에 위임
 * - 감쇠 공식: currentHour와 같은 버킷은 0.9, 이전 버킷은 0.1 가중치 적용
 *
 * @param calculator 랭킹 점수 계산기
 * @param currentHour 현재 시간 버킷 (기준 시간)
 * @param weight 가중치 설정
 */
class HourlyScoreProcessor(
    private val calculator: RankingScoreCalculator,
    private val currentHour: Instant,
    private val weight: RankingWeight,
) : ItemProcessor<ProductHourlyMetric, ScoreEntry> {

    override fun process(item: ProductHourlyMetric): ScoreEntry {
        return calculator.calculateSingleHourly(item, currentHour, weight)
    }
}
