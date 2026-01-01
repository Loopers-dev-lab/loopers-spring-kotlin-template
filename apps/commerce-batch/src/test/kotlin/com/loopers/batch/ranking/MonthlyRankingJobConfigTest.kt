package com.loopers.batch.ranking

import com.loopers.infrastructure.ProductRankMonthlyRepository
import com.loopers.support.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(properties = ["spring.task.scheduling.enabled=false"])
@DisplayName("월간 랭킹 Batch Job 통합 테스트")
class MonthlyRankingJobConfigTest @Autowired constructor(
    private val jobLauncher: JobLauncher,
    private val monthlyRankingJob: Job,
    private val productRankMonthlyRepository: ProductRankMonthlyRepository,
    private val redisTemplate: StringRedisTemplate
) {
    @BeforeEach
    fun setUp() {
        // Redis에 테스트 데이터 적재 (12월 1일~7일)
        val dates = DateUtils.getMonthDates("2025-12").take(7)
        dates.forEach { date ->
            val key = "ranking:all:${DateUtils.formatDate(date)}"
            redisTemplate.opsForZSet().add(key, "201", 5.0)
            redisTemplate.opsForZSet().add(key, "202", 10.0)
            redisTemplate.opsForZSet().add(key, "203", 8.0)
        }
    }

    @AfterEach
    fun tearDown() {
        productRankMonthlyRepository.deleteAll()

        val dates = DateUtils.getMonthDates("2025-12").take(7)
        dates.forEach { date ->
            val key = "ranking:all:${DateUtils.formatDate(date)}"
            redisTemplate.delete(key)
        }
    }

    @DisplayName("월간 랭킹 배치를 실행하면 TOP 100이 MV 테이블에 저장된다")
    @Test
    fun runMonthlyRankingJob() {
        // Given
        val yearMonth = "2025-12"
        val params = JobParametersBuilder()
            .addString("yearMonth", yearMonth)
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        // When
        val execution = jobLauncher.run(monthlyRankingJob, params)

        // Then
        assertThat(execution.status.isUnsuccessful).isFalse()

        val rankings = productRankMonthlyRepository.findByPeriodOrderByRankPositionAsc(yearMonth)
        assertThat(rankings).hasSize(3)
        assertThat(rankings[0].productId).isEqualTo(202L)
        assertThat(rankings[0].score).isEqualTo(70.0) // 10.0 * 7일
        assertThat(rankings[0].rankPosition).isEqualTo(1)
    }

    @DisplayName("동일한 월간 랭킹을 재실행하면 기존 데이터를 덮어쓴다 (멱등성)")
    @Test
    fun monthlyRankingJobIsIdempotent() {
        // Given
        val yearMonth = "2025-12"
        val params = JobParametersBuilder()
            .addString("yearMonth", yearMonth)
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        // When: 첫 실행
        jobLauncher.run(monthlyRankingJob, params)

        // Redis 데이터 변경
        val dates = DateUtils.getMonthDates(yearMonth).take(7)
        dates.forEach { date ->
            val key = "ranking:all:${DateUtils.formatDate(date)}"
            redisTemplate.opsForZSet().add(key, "201", 50.0) // 점수 변경
        }

        // 두 번째 실행 (timestamp 변경하여 재실행 허용)
        val params2 = JobParametersBuilder()
            .addString("yearMonth", yearMonth)
            .addLong("timestamp", System.currentTimeMillis() + 1000)
            .toJobParameters()
        jobLauncher.run(monthlyRankingJob, params2)

        // Then: 덮어쓰기 확인
        val rankings = productRankMonthlyRepository.findByPeriodOrderByRankPositionAsc(yearMonth)
        assertThat(rankings).hasSize(3)
        // 201번 상품이 1위로 변경되어야 함
        assertThat(rankings[0].productId).isEqualTo(201L)
        assertThat(rankings[0].score).isEqualTo(350.0) // 50.0 * 7일
    }
}
