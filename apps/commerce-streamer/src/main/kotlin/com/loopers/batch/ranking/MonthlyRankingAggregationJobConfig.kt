package com.loopers.batch.ranking

import com.loopers.domain.ranking.ProductRankDailyRepository
import com.loopers.domain.ranking.ProductRankMonthly
import com.loopers.domain.ranking.ProductRankMonthlyRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.parameters.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.batch.infrastructure.item.support.ListItemReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate
import java.time.YearMonth

/**
 * 월간 랭킹 집계 배치 Job
 *
 * - 매월 1일 02:00에 실행
 * - 지난 달의 product_rank_daily 데이터를 집계
 * - 상품별 평균 점수 계산하여 TOP 100 선정
 * - mv_product_rank_monthly 테이블에 저장
 */
@Configuration
class MonthlyRankingAggregationJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val productRankDailyRepository: ProductRankDailyRepository,
    private val productRankMonthlyRepository: ProductRankMonthlyRepository,
) {
    private val logger = LoggerFactory.getLogger(MonthlyRankingAggregationJobConfig::class.java)

    companion object {
        const val JOB_NAME = "monthlyRankingAggregationJob"
        const val STEP_NAME = "monthlyRankingAggregationStep"
        const val CHUNK_SIZE = 100
        const val TOP_N = 100
    }

    @Bean
    fun monthlyRankingAggregationJob(): Job = JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(monthlyRankingAggregationStep())
            .build()

    @Bean
    @JobScope
    fun monthlyRankingAggregationStep(): Step = StepBuilder(STEP_NAME, jobRepository)
            .chunk<MonthlyRankingAggregate, ProductRankMonthly>(CHUNK_SIZE, transactionManager)
            .reader(monthlyRankingReader(null))
            .processor(monthlyRankingProcessor())
            .writer(monthlyRankingWriter())
            .build()

    /**
     * Reader: product_rank_daily에서 지난 달 데이터 읽기
     */
    @Bean
    @StepScope
    fun monthlyRankingReader(
        @Value("#{jobParameters['targetYearMonth']}") targetYearMonthStr: String?,
    ): ItemReader<MonthlyRankingAggregate> {
        // targetYearMonth: 집계 대상 연월 (예: "202501")
        val targetYearMonth = targetYearMonthStr?.let {
            ProductRankMonthly.stringToYearMonth(it)
        } ?: YearMonth.now().minusMonths(1)

        val startDate = targetYearMonth.atDay(1)
        val endDate = targetYearMonth.atEndOfMonth()

        logger.info("월간 랭킹 Reader 초기화: yearMonth=$targetYearMonth, startDate=$startDate, endDate=$endDate")

        // DB에서 해당 월 데이터 조회
        val dailyRankings = productRankDailyRepository.findByRankingDateBetween(startDate, endDate)

        // 상품별로 집계
        val aggregates = dailyRankings
            .groupBy { it.productId }
            .map { (productId, rankings) ->
                val avgScore = rankings.map { it.score }.average()
                val dayCount = rankings.size

                MonthlyRankingAggregate(
                    productId = productId,
                    avgScore = avgScore,
                    dayCount = dayCount,
                    yearMonth = ProductRankMonthly.yearMonthToString(targetYearMonth),
                    startDate = startDate,
                    endDate = endDate,
                )
            }
            .sortedByDescending { it.avgScore }
            .take(TOP_N) // TOP 100만
            .mapIndexed { index, aggregate -> aggregate.copy(rank = index + 1) }

        logger.info("월간 랭킹 집계 데이터: count=${aggregates.size}, yearMonth=$targetYearMonth")

        return ListItemReader(aggregates)
    }

    /**
     * Processor: 집계 데이터를 ProductRankMonthly 엔티티로 변환
     */
    @Bean
    @StepScope
    fun monthlyRankingProcessor(): ItemProcessor<MonthlyRankingAggregate, ProductRankMonthly> = ItemProcessor { aggregate ->
            ProductRankMonthly(
                yearMonth = aggregate.yearMonth,
                productId = aggregate.productId,
                score = aggregate.avgScore,
                rank = aggregate.rank,
                periodStart = aggregate.startDate,
                periodEnd = aggregate.endDate,
            )
        }

    /**
     * Writer: mv_product_rank_monthly 테이블에 저장
     */
    @Bean
    @StepScope
    fun monthlyRankingWriter(): ItemWriter<ProductRankMonthly> {
        return ItemWriter { items ->
            if (items.isEmpty()) return@ItemWriter

            val yearMonth = items.first().yearMonth

            // 기존 데이터 삭제 (멱등성 보장)
            productRankMonthlyRepository.deleteByYearMonth(yearMonth)

            // 신규 데이터 저장
            productRankMonthlyRepository.saveAll(items.toList())

            logger.info("월간 랭킹 저장 완료: yearMonth=$yearMonth, count=${items.size()}")
        }
    }
}

/**
 * 월간 랭킹 집계용 DTO
 */
data class MonthlyRankingAggregate(
    val productId: Long,
    val avgScore: Double,
    val dayCount: Int,
    val yearMonth: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val rank: Int = 0,
)
