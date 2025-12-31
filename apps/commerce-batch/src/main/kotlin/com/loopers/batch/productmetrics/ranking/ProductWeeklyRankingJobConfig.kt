package com.loopers.batch.productmetrics.ranking

import com.loopers.domain.ranking.dto.ProductWeeklyMetricsAggregate
import com.loopers.domain.ranking.dto.RankedProduct
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.database.JdbcPagingItemReader
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

/**
 * 주간 상품 랭킹 집계 Job 설정
 *
 * - Job: PRODUCT_WEEKLY_RANKING_JOB
 * - Step: PRODUCT_WEEKLY_RANKING_STEP
 * - Chunk 크기: 100개
 * - 흐름: Reader -> Processor -> Writer
 *   - Reader: product_metrics를 날짜별로 집계하여 원본 메트릭 반환
 *   - Processor: 날짜별 감쇠 가중치 계산 (D+0: 1.0 ~ D+6: 0.1)
 *   - Writer: productId별로 점수 합산 후 전체 데이터를 점수순 정렬하여 Top 100만 저장
 */
@Configuration
class ProductWeeklyRankingJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
) {

    @Bean(name = [JOB_NAME])
    fun productWeeklyRankingJob(
        @Qualifier(STEP_NAME) step: Step,
    ): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .preventRestart()
            .start(step)
            .build()
    }

    @Bean(name = [STEP_NAME])
    fun productWeeklyRankingStep(
        @Qualifier("weeklyRankingReader") productWeeklyRankingReader: JdbcPagingItemReader<ProductWeeklyMetricsAggregate>,
        productWeeklyRankingProcessor: ProductWeeklyRankingProcessor,
        productWeeklyRankingWriter: ProductWeeklyRankingWriter,
    ): Step {
        return StepBuilder(STEP_NAME, jobRepository)
            .chunk<ProductWeeklyMetricsAggregate, RankedProduct>(CHUNK_SIZE, transactionManager)
            .reader(productWeeklyRankingReader)
            .processor(productWeeklyRankingProcessor)
            .writer(productWeeklyRankingWriter)
            .build()
    }

    companion object {
        const val JOB_NAME = "PRODUCT_WEEKLY_RANKING_JOB"
        const val STEP_NAME = "PRODUCT_WEEKLY_RANKING_STEP"
        const val CHUNK_SIZE = 100
    }
}
