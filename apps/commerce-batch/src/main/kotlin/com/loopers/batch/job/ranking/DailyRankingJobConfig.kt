package com.loopers.batch.job.ranking

import com.loopers.batch.job.ranking.step.DailyMetricReader
import com.loopers.batch.job.ranking.step.DailyScoreProcessor
import com.loopers.batch.job.ranking.step.RedisAggregationWriter
import com.loopers.batch.job.ranking.step.ScoreEntry
import com.loopers.batch.listener.ChunkListener
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.SkipLoggingListener
import com.loopers.batch.listener.StepMonitorListener
import com.loopers.domain.ranking.ProductDailyMetric
import com.loopers.domain.ranking.RankingScoreCalculator
import com.loopers.domain.ranking.RankingWeight
import com.loopers.domain.ranking.RankingWeightRepository
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.dao.TransientDataAccessException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate
import javax.sql.DataSource

/**
 * DailyRankingJobConfig - 일별 랭킹 배치 Job 설정
 *
 * 1-Step 구조:
 * - Step 1 (dailyMetricAggregationStep): ProductDailyMetric 읽기 -> 점수 계산 -> Redis 집계
 *
 * 일별 랭킹은 Redis에만 저장되므로 RDB 저장 Step이 없음
 *
 * 트리거: 01:00, 13:00
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = DailyRankingJobConfig.JOB_NAME)
@Configuration
class DailyRankingJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val chunkListener: ChunkListener,
    private val skipLoggingListener: SkipLoggingListener,
    private val dataSource: DataSource,
    private val redisTemplate: RedisTemplate<String, String>,
    private val rankingScoreCalculator: RankingScoreCalculator,
    private val rankingWeightRepository: RankingWeightRepository,
) {
    companion object {
        const val JOB_NAME = "dailyRankingJob"
        private const val STEP_METRIC_AGGREGATION = "dailyMetricAggregationStep"
        private const val CHUNK_SIZE = 1000
        private const val RETRY_LIMIT = 1
        private const val SKIP_LIMIT = 10
    }

    @Bean(JOB_NAME)
    fun dailyRankingJob(): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .start(dailyMetricAggregationStep(null))
            .listener(jobListener)
            .build()
    }

    @JobScope
    @Bean(STEP_METRIC_AGGREGATION)
    fun dailyMetricAggregationStep(
        @Value("#{jobParameters['baseDate']}") baseDateParam: LocalDate?,
    ): Step {
        val baseDate = baseDateParam ?: LocalDate.now()
        val periodType = RankingPeriodType.DAILY

        val weight = rankingWeightRepository.findLatest() ?: RankingWeight.fallback()

        val reader = DailyMetricReader(
            dataSource = dataSource,
            baseDate = baseDate,
        )

        val processor = DailyScoreProcessor(
            calculator = rankingScoreCalculator,
            baseDate = baseDate,
            weight = weight,
        )

        val writer = RedisAggregationWriter(
            redisTemplate = redisTemplate,
            baseDate = baseDate,
            periodType = periodType,
        )

        return StepBuilder(STEP_METRIC_AGGREGATION, jobRepository)
            .chunk<ProductDailyMetric, ScoreEntry>(CHUNK_SIZE, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .retry(PessimisticLockingFailureException::class.java)
            .retry(TransientDataAccessException::class.java)
            .retryLimit(RETRY_LIMIT)
            .skip(DataIntegrityViolationException::class.java)
            .skip(NumberFormatException::class.java)
            .skipLimit(SKIP_LIMIT)
            .listener(stepMonitorListener)
            .listener(chunkListener)
            .listener(skipLoggingListener)
            .stream(writer)
            .build()
    }
}
