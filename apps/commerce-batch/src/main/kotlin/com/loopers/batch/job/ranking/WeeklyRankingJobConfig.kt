package com.loopers.batch.job.ranking

import com.loopers.batch.job.ranking.step.MetricAggregationReader
import com.loopers.batch.job.ranking.step.RankingPersistenceTasklet
import com.loopers.batch.job.ranking.step.RedisAggregationWriter
import com.loopers.batch.job.ranking.step.ScoreCalculationProcessor
import com.loopers.batch.job.ranking.step.ScoreEntry
import com.loopers.batch.listener.ChunkListener
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import com.loopers.domain.ranking.ProductDailyMetric
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

/**
 * WeeklyRankingJobConfig - 주간 랭킹 배치 Job 설정
 *
 * 2-Step 구조:
 * - Step 1 (metricAggregationStep): ProductDailyMetric 읽기 -> 점수 계산 -> Redis 집계
 * - Step 2 (rankingPersistenceStep): Redis TOP 100 추출 -> RDB 저장
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = WeeklyRankingJobConfig.JOB_NAME)
@Configuration
class WeeklyRankingJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val chunkListener: ChunkListener,
    private val metricAggregationReader: MetricAggregationReader,
    private val scoreCalculationProcessor: ScoreCalculationProcessor,
    private val redisAggregationWriter: RedisAggregationWriter,
    private val rankingPersistenceTasklet: RankingPersistenceTasklet,
) {
    companion object {
        const val JOB_NAME = "weeklyRankingJob"
        private const val STEP_METRIC_AGGREGATION = "metricAggregationStep"
        private const val STEP_RANKING_PERSISTENCE = "rankingPersistenceStep"
        private const val CHUNK_SIZE = 1000
    }

    @Bean(JOB_NAME)
    fun weeklyRankingJob(): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(metricAggregationStep())
            .next(rankingPersistenceStep())
            .listener(jobListener)
            .build()
    }

    @JobScope
    @Bean(STEP_METRIC_AGGREGATION)
    fun metricAggregationStep(): Step {
        return StepBuilder(STEP_METRIC_AGGREGATION, jobRepository)
            .chunk<ProductDailyMetric, ScoreEntry>(CHUNK_SIZE, transactionManager)
            .reader(metricAggregationReader)
            .processor(scoreCalculationProcessor)
            .writer(redisAggregationWriter)
            .listener(stepMonitorListener)
            .listener(chunkListener)
            .build()
    }

    @JobScope
    @Bean(STEP_RANKING_PERSISTENCE)
    fun rankingPersistenceStep(): Step {
        return StepBuilder(STEP_RANKING_PERSISTENCE, jobRepository)
            .tasklet(rankingPersistenceTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build()
    }
}
