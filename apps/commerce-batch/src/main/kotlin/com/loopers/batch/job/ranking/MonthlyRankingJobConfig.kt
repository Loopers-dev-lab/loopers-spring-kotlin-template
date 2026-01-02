package com.loopers.batch.job.ranking

import com.loopers.batch.job.ranking.step.MetricAggregationReader
import com.loopers.batch.job.ranking.step.RankingPersistenceTasklet
import com.loopers.batch.job.ranking.step.RedisAggregationWriter
import com.loopers.batch.job.ranking.step.ScoreCalculationProcessor
import com.loopers.batch.job.ranking.step.ScoreEntry
import com.loopers.batch.listener.ChunkListener
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.SkipLoggingListener
import com.loopers.batch.listener.StepMonitorListener
import com.loopers.domain.ranking.ProductDailyMetric
import com.loopers.domain.ranking.ProductPeriodRankingRepository
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.support.SynchronizedItemStreamReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate
import javax.sql.DataSource

/**
 * MonthlyRankingJobConfig - 월간 랭킹 배치 Job 설정
 *
 * 2-Step 구조:
 * - Step 1 (metricAggregationStep): ProductDailyMetric 읽기 (30일 윈도우) -> 점수 계산 -> Redis 집계
 * - Step 2 (rankingPersistenceStep): Redis TOP 100 추출 -> RDB 저장 (mv_product_rank_monthly)
 *
 * 스테이징 키: ranking:products:monthly:{yyyyMMdd}:staging
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = MonthlyRankingJobConfig.JOB_NAME)
@Configuration
class MonthlyRankingJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val chunkListener: ChunkListener,
    private val skipLoggingListener: SkipLoggingListener,
    private val dataSource: DataSource,
    private val redisTemplate: RedisTemplate<String, String>,
    private val productPeriodRankingRepository: ProductPeriodRankingRepository,
) {
    companion object {
        const val JOB_NAME = "monthlyRankingJob"
        private const val STEP_METRIC_AGGREGATION = "monthlyMetricAggregationStep"
        private const val STEP_RANKING_PERSISTENCE = "monthlyRankingPersistenceStep"
        private const val CHUNK_SIZE = 1000
        private const val THREAD_POOL_SIZE = 4
    }

    @Bean("monthlyRankingTaskExecutor")
    fun monthlyRankingTaskExecutor(): TaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = THREAD_POOL_SIZE
        executor.maxPoolSize = THREAD_POOL_SIZE
        executor.setThreadNamePrefix("monthly-ranking-")
        executor.initialize()
        return executor
    }

    @Bean(JOB_NAME)
    fun monthlyRankingJob(): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .start(monthlyMetricAggregationStep(null))
            .next(monthlyRankingPersistenceStep(null))
            .listener(jobListener)
            .build()
    }

    @JobScope
    @Bean(STEP_METRIC_AGGREGATION)
    fun monthlyMetricAggregationStep(
        @Value("#{jobParameters['baseDate']}") baseDateParam: LocalDate?,
    ): Step {
        val baseDate = baseDateParam ?: LocalDate.now()
        val periodType = RankingPeriodType.MONTHLY

        val reader = MetricAggregationReader(
            dataSource = dataSource,
            baseDate = baseDate,
            windowDays = periodType.windowDays,
        )

        // JdbcPagingItemReader는 thread-safe하지 않으므로 SynchronizedItemStreamReader로 감싸서 사용
        val synchronizedReader = SynchronizedItemStreamReader<ProductDailyMetric>().apply {
            setDelegate(reader)
        }

        val writer = RedisAggregationWriter(
            redisTemplate = redisTemplate,
            baseDate = baseDate,
            periodType = periodType,
        )

        return StepBuilder(STEP_METRIC_AGGREGATION, jobRepository)
            .chunk<ProductDailyMetric, ScoreEntry>(CHUNK_SIZE, transactionManager)
            .reader(synchronizedReader)
            .processor(ScoreCalculationProcessor())
            .writer(writer)
            .taskExecutor(monthlyRankingTaskExecutor())
            .faultTolerant()
            .retry(Exception::class.java)
            .retryLimit(1)
            .skip(Exception::class.java)
            .skipLimit(10)
            .listener(stepMonitorListener)
            .listener(chunkListener)
            .listener(skipLoggingListener)
            .build()
    }

    @JobScope
    @Bean(STEP_RANKING_PERSISTENCE)
    fun monthlyRankingPersistenceStep(
        @Value("#{jobParameters['baseDate']}") baseDateParam: LocalDate?,
    ): Step {
        val baseDate = baseDateParam ?: LocalDate.now()
        val periodType = RankingPeriodType.MONTHLY

        val tasklet = RankingPersistenceTasklet(
            redisTemplate = redisTemplate,
            productPeriodRankingRepository = productPeriodRankingRepository,
            baseDate = baseDate,
            periodType = periodType,
        )

        return StepBuilder(STEP_RANKING_PERSISTENCE, jobRepository)
            .tasklet(tasklet, transactionManager)
            .listener(stepMonitorListener)
            .build()
    }
}
