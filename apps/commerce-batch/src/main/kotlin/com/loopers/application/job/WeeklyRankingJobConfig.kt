package com.loopers.application.job

import com.loopers.domain.productMetric.ProductMetric
import com.loopers.domain.productMetric.ProductMetricService
import com.loopers.domain.ranking.ProductRankWeekly
import com.loopers.domain.ranking.ProductRankWeeklyService
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemWriter
import org.springframework.batch.item.data.RepositoryItemReader
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Configuration
class WeeklyRankingJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val productMetricService: ProductMetricService,
    private val productRankWeeklyService: ProductRankWeeklyService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val CHUNK_SIZE = 1000
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
    }

    @Bean
    fun weeklyRankingJob(): Job {
        return JobBuilder("weeklyRankingJob", jobRepository)
            .start(deleteOldDataStep(null))
            .next(weeklyRankingStep())
            .build()
    }

    @Bean
    @StepScope
    fun deleteOldDataStep(
        @Value("#{jobParameters['targetDate']}") targetDate: String?,
    ): Step {
        val mondayDate = calculateMondayDate(targetDate!!)

        return StepBuilder("deleteOldDataStep", jobRepository)
            .tasklet(
                { _, _ ->
                    log.info("기존 데이터 삭제 시작 - week: $mondayDate")
                    productRankWeeklyService.deleteByDateTime(mondayDate)
                    log.info("기존 데이터 삭제 완료")
                    RepeatStatus.FINISHED
                },
                transactionManager,
            )
            .build()
    }

    @Bean
    fun weeklyRankingStep(): Step {
        return StepBuilder("weeklyRankingStep", jobRepository)
            .chunk<ProductMetric, ProductRankWeekly>(CHUNK_SIZE, transactionManager)
            .reader(weeklyRankingReader(null))
            .processor(weeklyRankingProcessor(null))
            .writer(weeklyRankingWriter(null))
            .build()
    }

    @Bean
    @StepScope
    fun weeklyRankingReader(
        @Value("#{jobParameters['targetDate']}") targetDate: String?,
    ): RepositoryItemReader<ProductMetric> {
        val (startDate, endDate) = calculateWeekRange(targetDate!!)
        log.info("주간 랭킹 Reader 시작 - targetDate: $targetDate, 범위: $startDate ~ $endDate")

        return productMetricService.createItemReader(startDate, endDate, CHUNK_SIZE)
    }

    @Bean
    @StepScope
    fun weeklyRankingProcessor(
        @Value("#{jobParameters['targetDate']}") targetDate: String?,
    ): ItemProcessor<ProductMetric, ProductRankWeekly> {
        return ItemProcessor { metric ->
            val score = calculateScore(metric)
            log.debug("Processing - productId: ${metric.refProductId}, score: $score")

            ProductRankWeekly.create(
                refProductId = metric.refProductId,
                score = score,
                likeCount = metric.likeCount,
                viewCount = metric.viewCount,
                salesCount = metric.salesCount,
            )
        }
    }

    @Bean
    @StepScope
    fun weeklyRankingWriter(
        @Value("#{jobParameters['targetDate']}") targetDate: String?,
    ): ItemWriter<ProductRankWeekly> {
        val mondayDate = calculateMondayDate(targetDate!!)

        return ItemWriter { chunk ->
            log.info("Writing ${chunk.items.size} items for week: $mondayDate")

            chunk.items.forEach { ranking ->
                productRankWeeklyService.saveOrUpdateScore(ranking, mondayDate)
            }
        }
    }

    @Bean
    @StepScope
    fun updateRankStep(
        @Value("#{jobParameters['targetDate']}") targetDate: String?,
    ): Step {
        val mondayDate = calculateMondayDate(targetDate!!)

        return StepBuilder("updateRankStep", jobRepository)
            .tasklet(
                { _, _ ->
                    log.info("순위 업데이트 시작 - week: $mondayDate")

                    val updatedCount = productRankWeeklyService.updateRanks(mondayDate)

                    log.info("순위 업데이트 완료 - 총 ${updatedCount}개")
                    RepeatStatus.FINISHED
                },
                transactionManager,
            )
            .build()
    }

    /**
     * 점수 계산
     * - viewCount: 30%
     * - likeCount: 50%
     * - salesCount: 20%
     */
    private fun calculateScore(metric: ProductMetric): Long {
        return ((metric.viewCount * 0.3) + (metric.likeCount * 0.5) + (metric.salesCount * 0.2)).toLong()
    }

    /**
     * 주어진 날짜가 속한 주의 월요일~일요일 범위 계산
     *
     * @param targetDate 대상 날짜 (형식: "yyyyMMdd")
     * @return Pair<시작일(월요일), 종료일(일요일)>
     */
    private fun calculateWeekRange(targetDate: String): Pair<String, String> {
        val date = LocalDate.parse(targetDate, DATE_FORMATTER)
        val monday = date.with(DayOfWeek.MONDAY)
        val sunday = date.with(DayOfWeek.SUNDAY)

        return monday.format(DATE_FORMATTER) to sunday.format(DATE_FORMATTER)
    }

    /**
     * 주어진 날짜가 속한 주의 월요일 날짜 계산
     *
     * @param targetDate 대상 날짜 (형식: "yyyyMMdd")
     * @return 월요일 날짜 (형식: "yyyyMMdd")
     */
    private fun calculateMondayDate(targetDate: String): String {
        val date = LocalDate.parse(targetDate, DATE_FORMATTER)
        val monday = date.with(DayOfWeek.MONDAY)
        return monday.format(DATE_FORMATTER)
    }
}
