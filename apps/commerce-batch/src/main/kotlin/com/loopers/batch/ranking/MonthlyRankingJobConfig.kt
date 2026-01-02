package com.loopers.batch.ranking

import com.loopers.domain.ranking.ProductRankMonthly
import com.loopers.infrastructure.ProductRankMonthlyRepository
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
class MonthlyRankingJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val redisTemplate: RedisTemplate<String, String>,
    private val productRankMonthlyRepository: ProductRankMonthlyRepository,
    private val jobListener: com.loopers.batch.listener.JobListener,
    private val stepMonitorListener: com.loopers.batch.listener.StepMonitorListener,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun monthlyRankingJob(): Job {
        return JobBuilder("monthlyRankingJob", jobRepository)
            .listener(jobListener)
            .start(monthlyRankingStep())
            .build()
    }

    @Bean
    fun monthlyRankingStep(): Step {
        return StepBuilder("monthlyRankingStep", jobRepository)
            .chunk<MonthlyScoreData, ProductRankMonthly>(100, transactionManager)
            .reader(monthlyRankingReader(""))
            .processor(monthlyRankingProcessor())
            .writer(monthlyRankingWriter())
            .listener(monthlyDataCleanupListener())
            .listener(stepMonitorListener)
            .build()
    }

    @Bean
    @StepScope
    fun monthlyDataCleanupListener(): org.springframework.batch.core.StepExecutionListener {
        return object : org.springframework.batch.core.StepExecutionListener {
            override fun beforeStep(stepExecution: org.springframework.batch.core.StepExecution) {
                val yearMonth = stepExecution.jobParameters.getString("yearMonth") ?: return
                val deletedCount = productRankMonthlyRepository.deleteByPeriod(yearMonth)
                log.info("기존 월간 랭킹 데이터 삭제: $yearMonth (${deletedCount}건)")
            }
        }
    }

    @Bean
    @StepScope
    fun monthlyRankingReader(
        @Value("#{jobParameters['yearMonth']}") yearMonth: String,
    ): ItemReader<MonthlyScoreData> {
        log.info("===== 월간 랭킹 배치 시작: $yearMonth =====")

        // Redis에서 30일치 데이터 집계 및 TOP 100 선택
        val monthlyScores = aggregateMonthlyScores(yearMonth)
            .sortedByDescending { it.second }
            .take(100)
            .mapIndexed { index, (productId, score) ->
                MonthlyScoreData(productId, score, period = yearMonth, rank = (index + 1).toLong())
            }

        log.info("월간 집계 완료: ${monthlyScores.size}개 상품")

        // Iterator 기반 ItemReader
        val iterator = monthlyScores.iterator()
        return ItemReader {
            if (iterator.hasNext()) iterator.next() else null
        }
    }

    @Bean
    @StepScope
    fun monthlyRankingProcessor(): ItemProcessor<MonthlyScoreData, ProductRankMonthly> {
        return ItemProcessor { data ->
            ProductRankMonthly(
                productId = data.productId,
                period = data.period,
                score = data.score,
                rankPosition = data.rank,
            )
        }
    }

    data class MonthlyScoreData(
        val productId: Long,
        val score: Double,
        val period: String,
        val rank: Long,
    )

    @Bean
    fun monthlyRankingWriter(): ItemWriter<ProductRankMonthly> {
        return ItemWriter { items ->
            productRankMonthlyRepository.saveAll(items)
            log.info("월간 랭킹 ${items.size()}개 저장 완료")
        }
    }

    /**
     * Redis에서 30일치 데이터 합산
     */
    private fun aggregateMonthlyScores(
        yearMonth: String,
    ): List<Pair<Long, Double>> {
        val monthDates = DateUtils.getMonthDates(yearMonth)
        val productScores = mutableMapOf<Long, Double>()

        monthDates.forEach { date ->
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
