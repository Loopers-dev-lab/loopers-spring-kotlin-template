package com.loopers.batch.ranking

import com.loopers.infrastructure.ProductRankWeeklyRepository
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
@DisplayName("주간 랭킹 Batch Job 통합 테스트")
class WeeklyRankingJobConfigTest @Autowired constructor(
    private val jobLauncher: JobLauncher,
    private val weeklyRankingJob: Job,
    private val productRankWeeklyRepository: ProductRankWeeklyRepository,
    private val redisTemplate: StringRedisTemplate
) {
    @BeforeEach
    fun setUp() {
        // Redis에 테스트 데이터 적재(2025-W52: 12//22 ~ 12/28)
        val dates = DateUtils.getWeekDates("2025-W52")
        dates.forEach { date ->
            val key = "ranking:all:${DateUtils.formatDate(date)}"
            redisTemplate.opsForZSet().add(key, "101", 10.0)
            redisTemplate.opsForZSet().add(key, "102", 20.0)
            redisTemplate.opsForZSet().add(key, "103", 15.0)
        }
    }

    @AfterEach
    fun tearDown() {
        // MV 테이블 정리
        productRankWeeklyRepository.deleteAll()

        // Redis 키 정리
        val dates = DateUtils.getWeekDates("2025-W52")
        dates.forEach { date -> redisTemplate.delete("ranking:all:${DateUtils.formatDate(date)}") }
    }


    @DisplayName("주간 랭킹 배치를 실행하면 TOP 100이 MV 테이블에 저장된다")
    @Test
    fun runWeeklyRankingJob() {
        // Given
        val yearWeek = "2025-W52"
        val params = JobParametersBuilder()
            .addString("yearWeek", yearWeek)
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        // When
        val execution = jobLauncher.run(weeklyRankingJob, params)

        // Then
        assertThat(execution.status.isUnsuccessful).isFalse()

        val rankings = productRankWeeklyRepository.findByYearWeekOrderByRankPositionAsc(yearWeek)
        assertThat(rankings).hasSize(3)
        assertThat(rankings[0].productId).isEqualTo(102L)
        assertThat(rankings[0].score).isEqualTo(140.0) // 20.0 * 7일
        assertThat(rankings[0].rankPosition).isEqualTo(1)
    }

    @DisplayName("동일한 주간 랭킹을 재실행하면 기존 데이터를 덮어쓴다 (멱등성)")
    @Test
    fun weeklyRankingJobIsIdempotent() {
        // Given
        val yearWeek = "2025-W52"
        val params = JobParametersBuilder()
            .addString("yearWeek", yearWeek)
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        // When: 첫 실행
        jobLauncher.run(weeklyRankingJob, params)

        // Redis 데이터 변경
        val dates = DateUtils.getWeekDates("2025-W52")
        dates.forEach { date ->
            val key = "ranking:all:${DateUtils.formatDate(date)}"
            redisTemplate.opsForZSet().add(key, "105", 50.0) // 점수 변경
        }

        // 두 번째 실행 (timestamp 변경하여 재실행 허용)
        val params2 = JobParametersBuilder()
            .addString("yearWeek", yearWeek)
            .addLong("timestamp", System.currentTimeMillis() + 1000)
            .toJobParameters()
        jobLauncher.run(weeklyRankingJob, params2)

        // Then: 덮어쓰기 확인
        val rankings = productRankWeeklyRepository.findByYearWeekOrderByRankPositionAsc(yearWeek)
        assertThat(rankings).hasSize(4)
        // 105번 상품이 1위로 변경되어야 함
        assertThat(rankings[0].productId).isEqualTo(105L)
        assertThat(rankings[0].score).isEqualTo(350.0) // 50.0 * 7일
    }
}
