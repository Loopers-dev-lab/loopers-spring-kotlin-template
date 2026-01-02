package com.loopers.batch.job.ranking

import com.loopers.batch.job.ranking.step.HourlyMetricReader
import com.loopers.batch.job.ranking.step.HourlyScoreProcessor
import com.loopers.batch.job.ranking.step.RedisAggregationWriter
import com.loopers.batch.job.ranking.step.ScoreEntry
import com.loopers.batch.listener.ChunkListener
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.SkipLoggingListener
import com.loopers.batch.listener.StepMonitorListener
import com.loopers.domain.ranking.ProductHourlyMetric
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
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.sql.DataSource

/**
 * HourlyRankingJobConfig - 시간별 랭킹 배치 Job 설정
 *
 * 1-Step 구조:
 * - Step 1 (hourlyMetricAggregationStep): ProductHourlyMetric 읽기 -> 점수 계산 -> Redis 집계
 *
 * 시간별 랭킹은 Redis에만 저장되므로 RDB 저장 Step이 없음
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = HourlyRankingJobConfig.JOB_NAME)
@Configuration
class HourlyRankingJobConfig(
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
        const val JOB_NAME = "hourlyRankingJob"
        private const val STEP_METRIC_AGGREGATION = "hourlyMetricAggregationStep"
        private const val CHUNK_SIZE = 1000
        private const val RETRY_LIMIT = 1
        private const val SKIP_LIMIT = 10
    }

    @Bean(JOB_NAME)
    fun hourlyRankingJob(): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .start(hourlyMetricAggregationStep(null))
            .listener(jobListener)
            .build()
    }

    @JobScope
    @Bean(STEP_METRIC_AGGREGATION)
    fun hourlyMetricAggregationStep(
        @Value("#{jobParameters['baseDateTime']}") baseDateTimeParam: String?,
    ): Step {
        val baseDateTime = baseDateTimeParam?.let { Instant.parse(it) } ?: Instant.now()
        val currentHour = baseDateTime.truncatedTo(ChronoUnit.HOURS)
        val periodType = RankingPeriodType.HOURLY

        val weight = rankingWeightRepository.findLatest() ?: RankingWeight.fallback()

        val reader = HourlyMetricReader(
            dataSource = dataSource,
            baseDateTime = baseDateTime,
        )

        val processor = HourlyScoreProcessor(
            calculator = rankingScoreCalculator,
            currentHour = currentHour,
            weight = weight,
        )

        val writer = RedisAggregationWriter(
            redisTemplate = redisTemplate,
            baseDateTime = baseDateTime,
            periodType = periodType,
        )

        return StepBuilder(STEP_METRIC_AGGREGATION, jobRepository)
            .chunk<ProductHourlyMetric, ScoreEntry>(CHUNK_SIZE, transactionManager)
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
