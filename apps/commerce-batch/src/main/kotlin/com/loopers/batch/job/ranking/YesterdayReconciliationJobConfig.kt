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
 * YesterdayReconciliationJobConfig - 어제의 시간별 메트릭을 재집계하여 늦게 도착한 데이터 포함
 *
 * 1-Step 구조:
 * - Step 1 (yesterdayReconciliationStep): 어제의 ProductHourlyMetric 집계 읽기 -> ProductDailyMetric UPSERT
 *
 * 특징:
 * - TodayDailyRollupJob과 동일한 로직, 기본 날짜만 어제로 설정
 * - 04:00에 실행되어 Kafka 지연으로 늦게 도착한 데이터 포착
 * - Full recalculation 방식으로 멱등성 보장
 *
 * 실행 스케줄: 매일 04:00
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = YesterdayReconciliationJobConfig.JOB_NAME)
@Configuration
class YesterdayReconciliationJobConfig(
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
        const val JOB_NAME = "yesterdayReconciliationJob"
        private const val STEP_YESTERDAY_RECONCILIATION = "yesterdayReconciliationStep"
        private const val CHUNK_SIZE = 1000
        private const val RETRY_LIMIT = 1
        private const val SKIP_LIMIT = 10
    }

    @Bean(JOB_NAME)
    fun yesterdayReconciliationJob(): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .start(yesterdayReconciliationStep(null))
            .listener(jobListener)
            .build()
    }

    @JobScope
    @Bean(STEP_YESTERDAY_RECONCILIATION)
    fun yesterdayReconciliationStep(
        @Value("#{jobParameters['baseDate']}") baseDateParam: LocalDate?,
    ): Step {
        val baseDate = baseDateParam ?: LocalDate.now().minusDays(1)

        val reader = AggregatedHourlyMetricReader(
            dataSource = dataSource,
            targetDate = baseDate,
        )

        val writer = DailyMetricWriter(
            jdbcTemplate = jdbcTemplate,
        )

        return StepBuilder(STEP_YESTERDAY_RECONCILIATION, jobRepository)
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
