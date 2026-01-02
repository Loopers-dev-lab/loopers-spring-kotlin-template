package com.loopers.batch.job.ranking

import com.loopers.batch.job.ranking.step.AggregatedHourlyMetricReader
import com.loopers.batch.job.ranking.step.DailyMetricWriter
import com.loopers.batch.listener.ChunkListener
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.SkipLoggingListener
import com.loopers.batch.listener.StepMonitorListener
import com.loopers.domain.ranking.ProductDailyMetric
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate
import javax.sql.DataSource

/**
 * TodayDailyRollupJobConfig - 오늘의 시간별 메트릭을 일별 메트릭으로 집계
 *
 * 1-Step 구조:
 * - Step 1 (dailyRollupStep): ProductHourlyMetric 집계 읽기 -> ProductDailyMetric UPSERT
 *
 * 특징:
 * - Redis 저장 없이 RDB만 사용
 * - 프로세서 없이 Reader -> Writer 직접 연결
 * - Full recalculation 방식으로 멱등성 보장
 *
 * 실행 스케줄: 01:00, 07:00, 13:00, 19:00
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = TodayDailyRollupJobConfig.JOB_NAME)
@Configuration
class TodayDailyRollupJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val chunkListener: ChunkListener,
    private val skipLoggingListener: SkipLoggingListener,
    private val dataSource: DataSource,
    private val jdbcTemplate: JdbcTemplate,
) {
    companion object {
        const val JOB_NAME = "todayDailyRollupJob"
        private const val STEP_DAILY_ROLLUP = "todayDailyRollupStep"
        private const val CHUNK_SIZE = 1000
        private const val RETRY_LIMIT = 1
        private const val SKIP_LIMIT = 10
    }

    @Bean(JOB_NAME)
    fun todayDailyRollupJob(): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .start(dailyRollupStep(null))
            .listener(jobListener)
            .build()
    }

    @JobScope
    @Bean(STEP_DAILY_ROLLUP)
    fun dailyRollupStep(
        @Value("#{jobParameters['baseDate']}") baseDateParam: LocalDate?,
    ): Step {
        val baseDate = baseDateParam ?: LocalDate.now()

        val reader = AggregatedHourlyMetricReader(
            dataSource = dataSource,
            targetDate = baseDate,
        )

        val writer = DailyMetricWriter(
            jdbcTemplate = jdbcTemplate,
        )

        return StepBuilder(STEP_DAILY_ROLLUP, jobRepository)
            .chunk<ProductDailyMetric, ProductDailyMetric>(CHUNK_SIZE, transactionManager)
            .reader(reader)
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
            .build()
    }
}
