package com.loopers.batch.ranking

import com.loopers.domain.ranking.ProductRankDailyRepository
import com.loopers.domain.ranking.ProductRankWeekly
import com.loopers.domain.ranking.ProductRankWeeklyRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.parameters.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
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
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 주간 랭킹 집계 배치 Job
 *
 * - 매주 일요일 01:00에 실행
 * - 지난 7일간(월~일)의 product_rank_daily 데이터를 집계
 * - 상품별 평균 점수 계산하여 TOP 100 선정
 * - mv_product_rank_weekly 테이블에 저장
 */
@Configuration
class WeeklyRankingAggregationJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val productRankDailyRepository: ProductRankDailyRepository,
    private val productRankWeeklyRepository: ProductRankWeeklyRepository,
) {
    private val logger = LoggerFactory.getLogger(WeeklyRankingAggregationJobConfig::class.java)

    companion object {
        const val JOB_NAME = "weeklyRankingAggregationJob"
        const val DELETE_STEP_NAME = "weeklyRankingDeleteStep"
        const val AGGREGATE_STEP_NAME = "weeklyRankingAggregateStep"
        const val CHUNK_SIZE = 100
        const val TOP_N = 100
    }

    @Bean
    fun weeklyRankingAggregationJob(): Job = JobBuilder(JOB_NAME, jobRepository)
        .incrementer(RunIdIncrementer())
        .start(weeklyRankingDeleteStep(null))
        .next(weeklyRankingAggregateStep())
        .build()

    /**
     * Step 1: 기존 데이터 삭제 (멱등성 보장)
     */
    @Bean
    @JobScope
    fun weeklyRankingDeleteStep(
        @Value("#{jobParameters['targetDate']}") targetDateStr: String?,
    ): Step {
        val targetDate = targetDateStr?.let { LocalDate.parse(it) } ?: LocalDate.now().minusDays(1)
        val yearWeek = targetDate.format(DateTimeFormatter.ofPattern("YYYY'W'ww", Locale.KOREA))

        return StepBuilder(DELETE_STEP_NAME, jobRepository)
            .tasklet(
                { _, _ ->
                    productRankWeeklyRepository.deleteByYearWeek(yearWeek)
                    logger.info("주간 랭킹 기존 데이터 삭제 완료: yearWeek=$yearWeek")
                    null // null is interpreted as FINISHED
                },
                transactionManager,
            )
            .build()
    }

    /**
     * Step 2: 집계 및 저장
     */
    @Bean
    @JobScope
    fun weeklyRankingAggregateStep(): Step = StepBuilder(AGGREGATE_STEP_NAME, jobRepository)
        .chunk<WeeklyRankingAggregate, ProductRankWeekly>(CHUNK_SIZE)
        .reader(weeklyRankingReader(null))
        .processor(weeklyRankingProcessor())
        .writer(weeklyRankingWriter())
        .transactionManager(transactionManager)
        .build()

    /**
     * Reader: product_rank_daily에서 지난 7일 데이터 읽기
     */
    @Bean
    @StepScope
    fun weeklyRankingReader(
        @Value("#{jobParameters['targetDate']}") targetDateStr: String?,
    ): ItemReader<WeeklyRankingAggregate> {
        // targetDate: 집계 대상 주의 일요일 (주의 마지막 날)
        val targetDate = targetDateStr?.let { LocalDate.parse(it) } ?: LocalDate.now().minusDays(1)

        // 지난 7일 (월요일~일요일)
        val endDate = targetDate
        val startDate = targetDate.minusDays(6)

        logger.info("주간 랭킹 Reader 초기화: startDate=$startDate, endDate=$endDate")

        // DB에서 7일치 데이터 조회
        val dailyRankings = productRankDailyRepository.findByRankingDateBetween(startDate, endDate)

        // 상품별로 집계
        val aggregates = dailyRankings
            .groupBy { it.productId }
            .map { (productId, rankings) ->
                val avgScore = rankings.map { it.score }.average()
                val dayCount = rankings.size

                WeeklyRankingAggregate(
                    productId = productId,
                    avgScore = avgScore,
                    dayCount = dayCount,
                    startDate = startDate,
                    endDate = endDate,
                )
            }
            .sortedByDescending { it.avgScore }
            .take(TOP_N) // TOP 100만
            .mapIndexed { index, aggregate -> aggregate.copy(rank = index + 1) }

        logger.info("주간 랭킹 집계 데이터: count=${aggregates.size}, startDate=$startDate, endDate=$endDate")

        return ListItemReader(aggregates)
    }

    /**
     * Processor: 집계 데이터를 ProductRankWeekly 엔티티로 변환
     */
    @Bean
    @StepScope
    fun weeklyRankingProcessor(): ItemProcessor<WeeklyRankingAggregate, ProductRankWeekly> = ItemProcessor { aggregate ->
        val yearWeek = aggregate.endDate.format(DateTimeFormatter.ofPattern("YYYY'W'ww", Locale.KOREA))

        ProductRankWeekly(
            yearWeek = yearWeek,
            productId = aggregate.productId,
            score = aggregate.avgScore,
            rank = aggregate.rank,
            periodStart = aggregate.startDate,
            periodEnd = aggregate.endDate,
        )
    }

    /**
     * Writer: mv_product_rank_weekly 테이블에 저장
     */
    @Bean
    @StepScope
    fun weeklyRankingWriter(): ItemWriter<ProductRankWeekly> {
        return ItemWriter { items ->
            if (items.isEmpty) return@ItemWriter

            val yearWeek = items.first().yearWeek

            // 신규 데이터 저장
            productRankWeeklyRepository.saveAll(items.toList())

            logger.info("주간 랭킹 저장 완료: yearWeek=$yearWeek, count=${items.size()}")
        }
    }
}

/**
 * 주간 랭킹 집계용 DTO
 */
data class WeeklyRankingAggregate(
    val productId: Long,
    val avgScore: Double,
    val dayCount: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val rank: Int = 0,
)
