package com.loopers.infrastructure.ranking

import com.loopers.domain.product.ProductMetrics
import com.loopers.domain.product.ProductMetricsRepository
import com.loopers.domain.ranking.ProductRankDaily
import com.loopers.domain.ranking.ProductRankDailyRepository
import com.loopers.domain.ranking.Ranking
import com.loopers.domain.ranking.RankingKey
import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingScope
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 일간 랭킹 영구 저장 스케줄러
 * 매일 23:55에 Redis 랭킹 데이터를 DB에 저장
 */
@Component
class DailyRankingPersistenceScheduler(
    private val rankingRepository: RankingRepository,
    private val productRankDailyRepository: ProductRankDailyRepository,
    private val productMetricsRepository: ProductMetricsRepository,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(DailyRankingPersistenceScheduler::class.java)
        private const val MAX_RANK_TO_SAVE = 1000 // TOP 1000만 저장
    }

    /**
     * 일간 랭킹 영구 저장
     *
     * 매일 23:55에 실행:
     * - 오늘의 Redis 랭킹 데이터를 product_rank_daily 테이블에 저장
     * - TOP 1000까지 저장
     *
     * Cron: "0 55 23 * * *" = 매일 23시 55분 (Asia/Seoul)
     */
    @Scheduled(cron = "0 55 23 * * *", zone = "Asia/Seoul")
    @Transactional
    fun persistDailyRanking() {
        val today = LocalDate.now()

        try {
            logger.info("일간 랭킹 영구 저장 시작: date=$today")

            val key = RankingKey.daily(RankingScope.ALL, today)

            // Redis에서 TOP 1000 조회
            val rankings = rankingRepository.getTopN(key, 0, MAX_RANK_TO_SAVE - 1)

            if (rankings.isEmpty()) {
                logger.warn("일간 랭킹 영구 저장: 오늘 랭킹 데이터가 없음 - date=$today")
                return
            }

            // 상품 메트릭 조회 (한 번에)
            val productIds = rankings.map { it.productId }
            val metricsMap = productMetricsRepository.findAllByProductIdIn(productIds)
                .associateBy { it.productId }

            // 배치 저장
            val entities = rankings.map { ranking ->
                createProductRankDaily(
                    rankingDate = today,
                    ranking = ranking,
                    metrics = metricsMap[ranking.productId],
                )
            }

            // 기존 데이터 삭제 (멱등성 보장)
            productRankDailyRepository.deleteByRankingDate(today)

            // 신규 데이터 저장
            productRankDailyRepository.saveAll(entities)

            logger.info(
                "일간 랭킹 영구 저장 완료: date=$today, count=${rankings.size}",
            )
        } catch (e: Exception) {
            logger.error("일간 랭킹 영구 저장 실패: date=$today", e)
            throw e
        }
    }

    /**
     * Ranking과 ProductMetrics로부터 ProductRankDaily 생성
     */
    private fun createProductRankDaily(
        rankingDate: LocalDate,
        ranking: Ranking,
        metrics: ProductMetrics?,
    ): ProductRankDaily = ProductRankDaily(
        rankingDate = rankingDate,
        productId = ranking.productId,
        score = ranking.score.value,
        rank = ranking.rank,
        likeCount = metrics?.likeCount ?: 0,
        viewCount = metrics?.viewCount ?: 0,
        salesCount = metrics?.salesCount ?: 0,
    )
}
