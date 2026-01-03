package com.loopers.batch.ranking

import com.loopers.domain.ranking.ProductRankDailyRepository
import com.loopers.domain.ranking.ProductRankWeekly
import com.loopers.domain.ranking.ProductRankWeeklyRepository
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

/**
 * WeeklyRankingAggregationJob 통합 테스트
 *
 * 주간 랭킹 집계 배치 Job의 전체 프로세스를 테스트합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestContainersConfig::class)
@DisplayName("주간 랭킹 집계 배치 통합 테스트")
class WeeklyRankingAggregationJobIntegrationTest @Autowired constructor(
    private val jobOperator: JobOperator,
    jobRepository: JobRepository,
    @param:Qualifier("weeklyRankingAggregationJob") private val weeklyRankingAggregationJob: Job,
    private val productRankDailyRepository: ProductRankDailyRepository,
    private val productRankWeeklyRepository: ProductRankWeeklyRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(WeeklyRankingAggregationJobIntegrationTest::class.java)
    }

    private val helper = RankingJobTestHelper(jobRepository, productRankDailyRepository)

    @AfterEach
    fun tearDown() {
        try {
            databaseCleanUp.truncateAllTables()
        } catch (e: Exception) {
            // ignore if table doesn't exist
            logger.warn("tearDown warning: ${e.message}")
        }
    }

    @Test
    fun `7일간의 일간 랭킹을 집계하여 주간 랭킹 TOP 100을 생성한다`() {
        // given: 7일간의 일간 랭킹 데이터 생성
        val targetDate = LocalDate.of(2025, 1, 19) // 일요일
        val startDate = targetDate.minusDays(6) // 월요일

        // 상품 1: 매일 100점 (7일 평균 = 100)
        helper.createDailyRankings(startDate, 7, 1L, 100.0, 1)

        // 상품 2: 매일 90점 (7일 평균 = 90)
        helper.createDailyRankings(startDate, 7, 2L, 90.0, 2)

        // 상품 3: 5일만 등장 (점수 80, 평균 = 80)
        helper.createDailyRankings(startDate, 5, 3L, 80.0, 3)

        // when: 주간 랭킹 집계 배치 실행
        val jobParameters = JobParametersBuilder()
            .addString("targetDate", targetDate.toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val jobExecution = jobOperator.run(weeklyRankingAggregationJob, jobParameters)
        val completedExecution = helper.waitForCompletion(jobExecution)

        // then: Job이 성공적으로 완료되어야 함
        assertThat(completedExecution.status).isEqualTo(BatchStatus.COMPLETED)

        // 주간 랭킹 확인
        val yearWeek = ProductRankWeekly.yearWeekToString(targetDate)
        val weeklyRankings = productRankWeeklyRepository.findTopByYearWeekOrderByRank(yearWeek, 100)

        assertThat(weeklyRankings).hasSize(3)

        // 순위 확인 (평균 점수 기준)
        val rank1 = weeklyRankings.find { it.rank == 1 }!!
        assertThat(rank1.productId).isEqualTo(1L)
        assertThat(rank1.score).isEqualTo(100.0)
        assertThat(rank1.periodStart).isEqualTo(startDate)
        assertThat(rank1.periodEnd).isEqualTo(targetDate)

        val rank2 = weeklyRankings.find { it.rank == 2 }!!
        assertThat(rank2.productId).isEqualTo(2L)
        assertThat(rank2.score).isEqualTo(90.0)

        val rank3 = weeklyRankings.find { it.rank == 3 }!!
        assertThat(rank3.productId).isEqualTo(3L)
        assertThat(rank3.score).isEqualTo(80.0)
    }

    @Test
    fun `TOP 100까지만 저장한다`() {
        // given: 150개 상품의 7일간 랭킹 데이터
        val targetDate = LocalDate.of(2025, 1, 19)
        val startDate = targetDate.minusDays(6)

        for (productId in 1L..150L) {
            helper.createDailyRankings(
                startDate = startDate,
                daysCount = 7,
                productId = productId,
                score = (150 - productId).toDouble(), // 점수 내림차순
                rank = productId.toInt(),
            )
        }

        // when: 주간 랭킹 집계 배치 실행
        val jobParameters = JobParametersBuilder()
            .addString("targetDate", targetDate.toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val jobExecution = jobOperator.run(weeklyRankingAggregationJob, jobParameters)
        val completedExecution = helper.waitForCompletion(jobExecution)

        // then: TOP 100까지만 저장되어야 함
        assertThat(completedExecution.status).isEqualTo(BatchStatus.COMPLETED)

        val yearWeek = ProductRankWeekly.yearWeekToString(targetDate)
        val weeklyRankings = productRankWeeklyRepository.findTopByYearWeekOrderByRank(yearWeek, 100)

        assertThat(weeklyRankings).hasSize(100)
        assertThat(weeklyRankings.map { it.rank }).containsExactlyElementsOf(1..100)

        // 101위 이하는 저장되지 않아야 함 (yearWeek으로 다시 조회)
        val allForYearWeek = productRankWeeklyRepository.findByYearWeek(yearWeek)
        assertThat(allForYearWeek).hasSize(100)
    }

    @Test
    fun `동일 주차에 대해 재실행 시 기존 데이터를 삭제하고 새로 저장한다 - 멱등성`() {
        // given: 첫 번째 실행
        val targetDate = LocalDate.of(2025, 1, 19)
        val startDate = targetDate.minusDays(6)

        // 초기 데이터
        helper.createDailyRankings(startDate, 7, 1L, 100.0, 1)

        val jobParameters1 = JobParametersBuilder()
            .addString("targetDate", targetDate.toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()
        val jobExecution1 = jobOperator.run(weeklyRankingAggregationJob, jobParameters1)
        helper.waitForCompletion(jobExecution1)
        val yearWeek = ProductRankWeekly.yearWeekToString(targetDate)
        val firstRun = productRankWeeklyRepository.findTopByYearWeekOrderByRank(yearWeek, 100)
        assertThat(firstRun).hasSize(1)
        assertThat(firstRun[0].score).isEqualTo(100.0)

        // when: 데이터 변경 후 재실행
        // 기존 데이터 삭제
        for (day in 0..6) {
            productRankDailyRepository.deleteByRankingDate(startDate.plusDays(day.toLong()))
        }

        // 새로운 데이터 추가
        helper.createDailyRankings(startDate, 7, 1L, 80.0, 2)
        helper.createDailyRankings(startDate, 7, 2L, 120.0, 1)

        val jobParameters2 = JobParametersBuilder()
            .addString("targetDate", targetDate.toString())
            .addLong("timestamp", System.currentTimeMillis() + 1000)
            .toJobParameters()

        val jobExecution2 = jobOperator.run(weeklyRankingAggregationJob, jobParameters2)
        helper.waitForCompletion(jobExecution2)

        // then: 새로운 데이터로 덮어써져야 함
        val secondRun = productRankWeeklyRepository.findTopByYearWeekOrderByRank(yearWeek, 100)
        assertThat(secondRun).hasSize(2)

        val newRank1 = secondRun.find { it.rank == 1 }!!
        assertThat(newRank1.productId).isEqualTo(2L)
        assertThat(newRank1.score).isEqualTo(120.0)

        val newRank2 = secondRun.find { it.rank == 2 }!!
        assertThat(newRank2.productId).isEqualTo(1L)
        assertThat(newRank2.score).isEqualTo(80.0)
    }

    @Test
    fun `일간 랭킹 데이터가 없으면 주간 랭킹도 생성되지 않는다`() {
        // given: 일간 랭킹 데이터 없음
        val targetDate = LocalDate.of(2025, 1, 19)

        // when: 주간 랭킹 집계 배치 실행
        val jobParameters = JobParametersBuilder()
            .addString("targetDate", targetDate.toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val jobExecution = jobOperator.run(weeklyRankingAggregationJob, jobParameters)
        val completedExecution = helper.waitForCompletion(jobExecution)

        // then: Job은 성공하지만 데이터는 저장되지 않음
        assertThat(completedExecution.status).isEqualTo(BatchStatus.COMPLETED)

        val yearWeek = ProductRankWeekly.yearWeekToString(targetDate)
        val weeklyRankings = productRankWeeklyRepository.findTopByYearWeekOrderByRank(yearWeek, 100)
        assertThat(weeklyRankings).isEmpty()
    }

    @Test
    fun `변동하는 점수의 평균을 올바르게 계산한다`() {
        // given: 상품 점수가 요일별로 다름
        val targetDate = LocalDate.of(2025, 1, 19)
        val startDate = targetDate.minusDays(6)

        // 월: 100, 화: 90, 수: 110, 목: 95, 금: 105, 토: 100, 일: 100
        // 평균 = (100 + 90 + 110 + 95 + 105 + 100 + 100) / 7 = 700 / 7 = 100.0
        val scores = listOf(100.0, 90.0, 110.0, 95.0, 105.0, 100.0, 100.0)
        helper.createDailyRankings(startDate, 1L, scores, 1)

        // when: 주간 랭킹 집계
        val jobParameters = JobParametersBuilder()
            .addString("targetDate", targetDate.toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val jobExecution = jobOperator.run(weeklyRankingAggregationJob, jobParameters)
        helper.waitForCompletion(jobExecution)

        // then: 평균 점수가 정확히 계산되어야 함
        val yearWeek = ProductRankWeekly.yearWeekToString(targetDate)
        val weeklyRankings = productRankWeeklyRepository.findTopByYearWeekOrderByRank(yearWeek, 100)

        assertThat(weeklyRankings).hasSize(1)
        assertThat(weeklyRankings[0].score).isEqualTo(100.0)
    }
}
