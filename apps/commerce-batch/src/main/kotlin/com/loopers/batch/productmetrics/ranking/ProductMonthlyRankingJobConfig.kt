package com.loopers.batch.productmetrics.ranking

import com.loopers.domain.ranking.dto.ProductMonthlyMetricsAggregate
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
 * 월간 상품 랭킹 집계 Job 설정
 *
 * - Job: PRODUCT_MONTHLY_RANKING_JOB
 * - Step: PRODUCT_MONTHLY_RANKING_STEP
 * - Chunk 크기: 100개
 * - 흐름: Reader -> Processor -> Writer
 *   - Reader: product_metrics를 월 단위로 집계하여 원본 메트릭 반환
 *   - Processor: 가중치 계산 (조회수 * 0.1 + 주문수 * 0.7 + 좋아요 * 0.2)
 *   - Writer: 전체 데이터를 점수순 정렬하여 Top 100만 저장
 */
@Configuration
class ProductMonthlyRankingJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
) {

    @Bean(name = [JOB_NAME])
    fun productMonthlyRankingJob(
        @Qualifier(STEP_NAME) step: Step,
    ): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .preventRestart()
            .start(step)
            .build()
    }

    @Bean(name = [STEP_NAME])
    fun productMonthlyRankingStep(
        @Qualifier("monthlyRankingReader") productMonthlyRankingReader: JdbcPagingItemReader<ProductMonthlyMetricsAggregate>,
        productMonthlyRankingProcessor: ProductMonthlyRankingProcessor,
        productMonthlyRankingWriter: ProductMonthlyRankingWriter,
    ): Step {
        return StepBuilder(STEP_NAME, jobRepository)
            .chunk<ProductMonthlyMetricsAggregate, RankedProduct>(CHUNK_SIZE, transactionManager)
            .reader(productMonthlyRankingReader)
            .processor(productMonthlyRankingProcessor)
            .writer(productMonthlyRankingWriter)
            .build()
    }

    companion object {
        const val JOB_NAME = "PRODUCT_MONTHLY_RANKING_JOB"
        const val STEP_NAME = "PRODUCT_MONTHLY_RANKING_STEP"
        const val CHUNK_SIZE = 100
    }
}
