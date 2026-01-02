package com.loopers.batch.job.ranking.step

import com.loopers.batch.job.ranking.RankingPeriodType
import com.loopers.domain.ranking.MvProductRankMonthly
import com.loopers.domain.ranking.MvProductRankWeekly
import com.loopers.domain.ranking.ProductPeriodRankingRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.data.redis.core.RedisTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * RankingPersistenceTasklet - Step 2: TOP 100 추출 및 RDB 저장
 *
 * 수행 순서:
 * 1. Redis 스테이징 키에서 ZREVRANGE 0~99로 TOP 100 추출 (점수 내림차순)
 * 2. 해당 baseDate의 기존 랭킹 데이터 삭제
 * 3. 새로운 랭킹 데이터를 RDB에 저장
 * 4. 스테이징 키 삭제 (실패해도 Job은 성공으로 처리 - TTL로 자동 만료)
 */
class RankingPersistenceTasklet(
    private val redisTemplate: RedisTemplate<String, String>,
    private val productPeriodRankingRepository: ProductPeriodRankingRepository,
    private val baseDate: LocalDate,
    private val periodType: RankingPeriodType,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val STAGING_SUFFIX = ":staging"
        private const val TOP_RANKING_LIMIT = 100L
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
    }

    private val stagingKey: String by lazy {
        "${periodType.redisPrefix}:${DATE_FORMATTER.format(baseDate)}$STAGING_SUFFIX"
    }

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        log.info("Step 2 시작 - baseDate: {}, periodType: {}, stagingKey: {}", baseDate, periodType, stagingKey)

        // 1. Redis에서 TOP 100 추출 (ZREVRANGE with SCORES)
        val topRankings = fetchTopRankingsFromRedis()
        log.info("Redis에서 {} 개의 랭킹 데이터 추출", topRankings.size)

        if (topRankings.isEmpty()) {
            log.warn("스테이징 키에 데이터가 없습니다: {}", stagingKey)
            return RepeatStatus.FINISHED
        }

        // 2. 기존 랭킹 삭제
        val deletedCount = when (periodType) {
            RankingPeriodType.WEEKLY -> productPeriodRankingRepository.deleteWeeklyByBaseDate(baseDate)
            RankingPeriodType.MONTHLY -> productPeriodRankingRepository.deleteMonthlyByBaseDate(baseDate)
            RankingPeriodType.HOURLY, RankingPeriodType.DAILY ->
                throw UnsupportedOperationException("$periodType does not support RDB persistence")
        }
        log.info("기존 {} 랭킹 {} 개 삭제 완료 - baseDate: {}", periodType, deletedCount, baseDate)

        // 3. 새로운 랭킹 저장 (rank는 1부터 시작)
        val savedCount = when (periodType) {
            RankingPeriodType.WEEKLY -> saveWeeklyRankings(topRankings)
            RankingPeriodType.MONTHLY -> saveMonthlyRankings(topRankings)
            RankingPeriodType.HOURLY, RankingPeriodType.DAILY ->
                throw UnsupportedOperationException("$periodType does not support RDB persistence")
        }
        log.info("새로운 {} 랭킹 {} 개 저장 완료 - baseDate: {}", periodType, savedCount, baseDate)

        // 4. 스테이징 키 삭제 (실패해도 무시 - TTL로 자동 만료)
        deleteStagingKey()

        // StepContribution에 쓰기 수 기록
        contribution.incrementWriteCount(savedCount.toLong())

        log.info("Step 2 완료 - {} 개의 {} 랭킹 저장됨", savedCount, periodType)
        return RepeatStatus.FINISHED
    }

    /**
     * 주간 랭킹을 저장한다
     */
    private fun saveWeeklyRankings(rankings: List<RankingEntry>): Int {
        val weeklyRankings = rankings.mapIndexed { index, entry ->
            MvProductRankWeekly.create(
                baseDate = baseDate,
                rank = index + 1,
                productId = entry.productId,
                score = entry.score,
            )
        }
        productPeriodRankingRepository.saveAllWeekly(weeklyRankings)
        return weeklyRankings.size
    }

    /**
     * 월간 랭킹을 저장한다
     */
    private fun saveMonthlyRankings(rankings: List<RankingEntry>): Int {
        val monthlyRankings = rankings.mapIndexed { index, entry ->
            MvProductRankMonthly.create(
                baseDate = baseDate,
                rank = index + 1,
                productId = entry.productId,
                score = entry.score,
            )
        }
        productPeriodRankingRepository.saveAllMonthly(monthlyRankings)
        return monthlyRankings.size
    }

    /**
     * Redis 스테이징 키에서 TOP 100을 점수 내림차순으로 추출한다
     */
    private fun fetchTopRankingsFromRedis(): List<RankingEntry> {
        val zSetOps = redisTemplate.opsForZSet()

        // ZREVRANGE with SCORES (점수 내림차순, 0부터 99까지 = TOP 100)
        val typedTuples = zSetOps.reverseRangeWithScores(stagingKey, 0, TOP_RANKING_LIMIT - 1)
            ?: return emptyList()

        return typedTuples.mapNotNull { tuple ->
            val productId = tuple.value?.toLongOrNull()
            val score = tuple.score

            if (productId != null && score != null) {
                RankingEntry(productId, BigDecimal.valueOf(score))
            } else {
                null
            }
        }
    }

    /**
     * 스테이징 키를 삭제한다 (실패해도 Job은 계속 진행)
     */
    private fun deleteStagingKey() {
        try {
            val deleted = redisTemplate.delete(stagingKey)
            if (deleted) {
                log.info("스테이징 키 삭제 완료: {}", stagingKey)
            } else {
                log.warn("스테이징 키가 이미 존재하지 않습니다: {}", stagingKey)
            }
        } catch (e: Exception) {
            // 스테이징 키 삭제 실패는 무시 (TTL로 자동 만료됨)
            log.warn("스테이징 키 삭제 실패 (무시됨): {} - {}", stagingKey, e.message)
        }
    }

    /**
     * 내부 데이터 클래스 - Redis에서 추출한 랭킹 엔트리
     */
    private data class RankingEntry(
        val productId: Long,
        val score: BigDecimal,
    )
}
