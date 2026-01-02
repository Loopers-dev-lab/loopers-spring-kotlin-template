package com.loopers.batch.ranking

import com.loopers.domain.ranking.ProductRankWeekly
import com.loopers.infrastructure.ProductRankWeeklyRepository
import com.loopers.support.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemReader
import org.springframework.batch.item.ItemWriter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class WeeklyRankingJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val redisTemplate: RedisTemplate<String, String>,
    private val productRankWeeklyRepository: ProductRankWeeklyRepository,
    private val jobListener: com.loopers.batch.listener.JobListener,
    private val stepMonitorListener: com.loopers.batch.listener.StepMonitorListener,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun weeklyRankingJob(): Job {
        return JobBuilder("weeklyRankingJob", jobRepository)
            .listener(jobListener)
            .start(weeklyRankingStep())
            .build()
    }

    @Bean
    fun weeklyRankingStep(): Step {
        return StepBuilder("weeklyRankingStep", jobRepository)
            .chunk<WeeklyScoreData, ProductRankWeekly>(100, transactionManager)
            .reader(weeklyRankingReader(""))
            .processor(weeklyRankingProcessor())
            .writer(weeklyRankingWriter())
            .listener(weeklyDataCleanupListener())
            .listener(stepMonitorListener)
            .build()
    }

    @Bean
    @StepScope
    fun weeklyDataCleanupListener(): org.springframework.batch.core.StepExecutionListener {
        return object : org.springframework.batch.core.StepExecutionListener {
            override fun beforeStep(stepExecution: org.springframework.batch.core.StepExecution) {
                val yearWeek = stepExecution.jobParameters.getString("yearWeek") ?: return
                val deletedCount = productRankWeeklyRepository.deleteByYearWeek(yearWeek)
                log.info("기존 주간 랭킹 데이터 삭제: $yearWeek (${deletedCount}건)")
            }
        }
    }

    @Bean
    @StepScope
    fun weeklyRankingReader(
        @Value("#{jobParameters['yearWeek']}") yearWeek: String,
    ): ItemReader<WeeklyScoreData> {
        log.info("===== 주간 랭킹 배치 시작: $yearWeek =====")

        // Redis에서 7일치 데이터 집계 및 TOP 100 선택
        val weeklyScores = aggregateWeeklyScores(yearWeek)
            .sortedByDescending { it.second }
            .take(100)
            .mapIndexed { index, (productId, score) ->
                WeeklyScoreData(productId, score, yearWeek, (index + 1).toLong())
            }

        log.info("주간 집계 완료: ${weeklyScores.size}개 상품")

        // Iterator 기반 ItemReader
        val iterator = weeklyScores.iterator()
        return ItemReader {
            if (iterator.hasNext()) iterator.next() else null
        }
    }

    @Bean
    @StepScope
    fun weeklyRankingProcessor(): ItemProcessor<WeeklyScoreData, ProductRankWeekly> {
        return ItemProcessor { data ->
            ProductRankWeekly(
                productId = data.productId,
                yearWeek = data.yearWeek,
                score = data.score,
                rankPosition = data.rank,
            )
        }
    }

    data class WeeklyScoreData(
        val productId: Long,
        val score: Double,
        val yearWeek: String,
        val rank: Long,
    )

    @Bean
    fun weeklyRankingWriter(): ItemWriter<ProductRankWeekly> {
        return ItemWriter { items ->
            productRankWeeklyRepository.saveAll(items)
            log.info("주간 랭킹 ${items.size}개 저장 완료")
        }
    }

    /**
     * Redis에서 7일치 데이터 합산
     */
    private fun aggregateWeeklyScores(yearWeek: String): List<Pair<Long, Double>> {
        val weekDates = DateUtils.getWeekDates(yearWeek)
        val productScores = mutableMapOf<Long, Double>()

        weekDates.forEach { date ->
            val key = "ranking:all:${DateUtils.formatDate(date)}"
            log.debug("Redis 키 조회: $key")

            val dailyRankings = redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, 0, -1) // 전체 조회

            dailyRankings?.forEach { typedTuple ->
                val productIdStr = typedTuple.value ?: return@forEach
                val productId = productIdStr.replace("product:", "").toLongOrNull() ?: return@forEach
                val score = typedTuple.score ?: 0.0

                productScores[productId] = productScores.getOrDefault(productId, 0.0) + score
            }
        }

        return productScores.toList()
    }
}
