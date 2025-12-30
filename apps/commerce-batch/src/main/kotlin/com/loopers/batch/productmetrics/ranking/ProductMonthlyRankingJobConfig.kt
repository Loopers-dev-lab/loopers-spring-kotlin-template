package com.loopers.batch.productmetrics.ranking

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
 * - Chunk 크기: 1,000개
 * - 흐름: Reader -> Writer
 *   - Reader: product_metrics를 월 단위로 집계하여 RankedProduct 반환
 *   - Writer: Top 100을 ProductMonthlyRankingRepository에 저장
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
        @Qualifier("monthlyRankingReader") productMonthlyRankingReader: JdbcPagingItemReader<RankedProduct>,
        productMonthlyRankingWriter: ProductMonthlyRankingWriter,
    ): Step {
        return StepBuilder(STEP_NAME, jobRepository)
            .chunk<RankedProduct, RankedProduct>(CHUNK_SIZE, transactionManager)
            .reader(productMonthlyRankingReader)
            .writer(productMonthlyRankingWriter)
            .build()
    }

    companion object {
        const val JOB_NAME = "PRODUCT_MONTHLY_RANKING_JOB"
        const val STEP_NAME = "PRODUCT_MONTHLY_RANKING_STEP"
        const val CHUNK_SIZE = 100
    }
}
