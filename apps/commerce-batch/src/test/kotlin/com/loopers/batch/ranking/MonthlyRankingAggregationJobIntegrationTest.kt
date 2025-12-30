package com.loopers.batch.ranking

import com.loopers.domain.ranking.ProductRankDailyRepository
import com.loopers.domain.ranking.ProductRankMonthly
import com.loopers.domain.ranking.ProductRankMonthlyRepository
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.explore.JobExplorer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.YearMonth
import java.util.Properties

/**
 * MonthlyRankingAggregationJob 통합 테스트
 *
 * 월간 랭킹 집계 배치 Job의 전체 프로세스를 테스트합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestContainersConfig::class)
@DisplayName("월간 랭킹 집계 배치 통합 테스트")
class MonthlyRankingAggregationJobIntegrationTest @Autowired constructor(
    private val jobOperator: JobOperator,
    private val jobExplorer: JobExplorer,
    private val productRankDailyRepository: ProductRankDailyRepository,
    private val productRankMonthlyRepository: ProductRankMonthlyRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val helper = RankingJobTestHelper(jobExplorer, productRankDailyRepository)

    companion object {
        private val logger = LoggerFactory.getLogger(MonthlyRankingAggregationJobIntegrationTest::class.java)
    }

    @AfterEach
    fun tearDown() {
        try {
            databaseCleanUp.truncateAllTables()
        } catch (e: Exception) {
            // 예상치 못한 정리 실패는 다음 테스트에 영향을 줄 수 있으므로 로깅
            logger.warn("Failed to clean up database tables: ${e.message}", e)
        }
    }

    @Test
    fun `한 달간의 일간 랭킹을 집계하여 월간 랭킹 TOP 100을 생성한다`() {
        // given: 2025년 1월 전체 데이터 (31일)
        val targetYearMonth = YearMonth.of(2025, 1)
        val startDate = targetYearMonth.atDay(1)
        val endDate = targetYearMonth.atEndOfMonth()

        // 상품 1: 매일 100점 (31일 평균 = 100)
        helper.createDailyRankings(startDate, 31, 1L, 100.0, 1)

        // 상품 2: 매일 90점 (31일 평균 = 90)
        helper.createDailyRankings(startDate, 31, 2L, 90.0, 2)

        // 상품 3: 15일만 등장 (점수 80, 평균 = 80)
        helper.createDailyRankings(startDate, 15, 3L, 80.0, 3)

        // when: 월간 랭킹 집계 배치 실행
        val targetYearMonthStr = ProductRankMonthly.yearMonthToString(targetYearMonth)
        val jobParameters = Properties().apply {
            setProperty("targetYearMonth", targetYearMonthStr)
            setProperty("timestamp", System.currentTimeMillis().toString())
        }

        val executionId = jobOperator.start(MonthlyRankingAggregationJobConfig.JOB_NAME, jobParameters)
        val jobExecution = helper.getJobExecution(executionId)

        // then: Job이 성공적으로 완료되어야 함
        assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

        // 월간 랭킹 확인
        val monthlyRankings = productRankMonthlyRepository.findTopByYearMonthOrderByRank(targetYearMonthStr, 100)

        assertThat(monthlyRankings).hasSize(3)

        // 순위 확인 (평균 점수 기준)
        val rank1 = monthlyRankings.find { it.rank == 1 }!!
        assertThat(rank1.productId).isEqualTo(1L)
        assertThat(rank1.score).isEqualTo(100.0)
        assertThat(rank1.yearMonth).isEqualTo(targetYearMonthStr)
        assertThat(rank1.periodStart).isEqualTo(startDate)
        assertThat(rank1.periodEnd).isEqualTo(endDate)

        val rank2 = monthlyRankings.find { it.rank == 2 }!!
        assertThat(rank2.productId).isEqualTo(2L)
        assertThat(rank2.score).isEqualTo(90.0)

        val rank3 = monthlyRankings.find { it.rank == 3 }!!
        assertThat(rank3.productId).isEqualTo(3L)
        assertThat(rank3.score).isEqualTo(80.0)
    }

    @Test
    fun `TOP 100까지만 저장한다`() {
        // given: 150개 상품의 1월 전체 랭킹 데이터
        val targetYearMonth = YearMonth.of(2025, 1)
        val startDate = targetYearMonth.atDay(1)

        for (productId in 1L..150L) {
            helper.createDailyRankings(
                startDate = startDate,
                daysCount = 31,
                productId = productId,
                score = (150 - productId).toDouble(), // 점수 내림차순
                rank = productId.toInt(),
            )
        }

        // when: 월간 랭킹 집계 배치 실행
        val targetYearMonthStr = ProductRankMonthly.yearMonthToString(targetYearMonth)
        val jobParameters = Properties().apply {
            setProperty("targetYearMonth", targetYearMonthStr)
            setProperty("timestamp", System.currentTimeMillis().toString())
        }

        val executionId = jobOperator.start(MonthlyRankingAggregationJobConfig.JOB_NAME, jobParameters)
        val jobExecution = helper.getJobExecution(executionId)

        // then: TOP 100까지만 저장되어야 함
        assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

        val monthlyRankings = productRankMonthlyRepository.findTopByYearMonthOrderByRank(targetYearMonthStr, 100)

        assertThat(monthlyRankings).hasSize(100)
        assertThat(monthlyRankings.map { it.rank }).containsExactlyElementsOf(1..100)

        // 101위 이하는 저장되지 않아야 함 (yearMonth로 다시 조회)
        val allForYearMonth = productRankMonthlyRepository.findByYearMonth(targetYearMonthStr)
        assertThat(allForYearMonth).hasSize(100)
    }

    @Test
    fun `동일 월에 대해 재실행 시 기존 데이터를 삭제하고 새로 저장한다 - 멱등성`() {
        // given: 첫 번째 실행
        val targetYearMonth = YearMonth.of(2025, 1)
        val targetYearMonthStr = ProductRankMonthly.yearMonthToString(targetYearMonth)
        val startDate = targetYearMonth.atDay(1)

        // 초기 데이터
        helper.createDailyRankings(startDate, 31, 1L, 100.0, 1)

        val jobParameters1 = Properties().apply {
            setProperty("targetYearMonth", targetYearMonthStr)
            setProperty("timestamp", System.currentTimeMillis().toString())
        }

        val executionId1 = jobOperator.start(MonthlyRankingAggregationJobConfig.JOB_NAME, jobParameters1)
        helper.getJobExecution(executionId1)

        val firstRun = productRankMonthlyRepository.findTopByYearMonthOrderByRank(targetYearMonthStr, 100)
        assertThat(firstRun).hasSize(1)
        assertThat(firstRun[0].score).isEqualTo(100.0)

        // when: 데이터 변경 후 재실행
        // 기존 데이터 삭제
        productRankDailyRepository.deleteByYearMonth(targetYearMonth)

        // 새로운 데이터 추가
        helper.createDailyRankings(startDate, 31, 1L, 80.0, 2)
        helper.createDailyRankings(startDate, 31, 2L, 120.0, 1)

        val jobParameters2 = Properties().apply {
            setProperty("targetYearMonth", targetYearMonthStr)
            setProperty("timestamp", (System.currentTimeMillis() + 1000).toString())
        }

        val executionId2 = jobOperator.start(MonthlyRankingAggregationJobConfig.JOB_NAME, jobParameters2)
        helper.getJobExecution(executionId2)

        // then: 새로운 데이터로 덮어써져야 함
        val secondRun = productRankMonthlyRepository.findTopByYearMonthOrderByRank(targetYearMonthStr, 100)
        assertThat(secondRun).hasSize(2)

        val newRank1 = secondRun.find { it.rank == 1 }!!
        assertThat(newRank1.productId).isEqualTo(2L)
        assertThat(newRank1.score).isEqualTo(120.0)

        val newRank2 = secondRun.find { it.rank == 2 }!!
        assertThat(newRank2.productId).isEqualTo(1L)
        assertThat(newRank2.score).isEqualTo(80.0)
    }

    @Test
    fun `일간 랭킹 데이터가 없으면 월간 랭킹도 생성되지 않는다`() {
        // given: 일간 랭킹 데이터 없음
        val targetYearMonth = YearMonth.of(2025, 1)
        val targetYearMonthStr = ProductRankMonthly.yearMonthToString(targetYearMonth)

        // when: 월간 랭킹 집계 배치 실행
        val jobParameters = Properties().apply {
            setProperty("targetYearMonth", targetYearMonthStr)
            setProperty("timestamp", System.currentTimeMillis().toString())
        }

        val executionId = jobOperator.start(MonthlyRankingAggregationJobConfig.JOB_NAME, jobParameters)
        val jobExecution = helper.getJobExecution(executionId)

        // then: Job은 성공하지만 데이터는 저장되지 않음
        assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

        val monthlyRankings = productRankMonthlyRepository.findTopByYearMonthOrderByRank(targetYearMonthStr, 100)
        assertThat(monthlyRankings).isEmpty()
    }

    @Test
    fun `변동하는 점수의 평균을 올바르게 계산한다`() {
        // given: 2월 (28일) 상품 점수가 날짜별로 다름
        val targetYearMonth = YearMonth.of(2025, 2)
        val targetYearMonthStr = ProductRankMonthly.yearMonthToString(targetYearMonth)
        val startDate = targetYearMonth.atDay(1)

        // 첫 14일: 100점, 나중 14일: 120점
        // 평균 = (100 * 14 + 120 * 14) / 28 = 3080 / 28 = 110.0
        helper.createDailyRankings(startDate, 14, 1L, 100.0, 1)
        helper.createDailyRankings(startDate.plusDays(14), 14, 1L, 120.0, 1)

        // when: 월간 랭킹 집계
        val jobParameters = Properties().apply {
            setProperty("targetYearMonth", targetYearMonthStr)
            setProperty("timestamp", System.currentTimeMillis().toString())
        }

        val executionId = jobOperator.start(MonthlyRankingAggregationJobConfig.JOB_NAME, jobParameters)
        helper.getJobExecution(executionId)

        // then: 평균 점수가 정확히 계산되어야 함
        val monthlyRankings = productRankMonthlyRepository.findTopByYearMonthOrderByRank(targetYearMonthStr, 100)

        assertThat(monthlyRankings).hasSize(1)
        assertThat(monthlyRankings[0].score).isEqualTo(110.0)
    }

    @Test
    fun `다른 월의 데이터는 집계에 포함되지 않는다`() {
        // given: 1월과 2월 데이터
        val januaryStart = LocalDate.of(2025, 1, 1)
        val februaryStart = LocalDate.of(2025, 2, 1)

        // 1월 데이터
        helper.createDailyRankings(januaryStart, 31, 1L, 100.0, 1)

        // 2월 데이터
        helper.createDailyRankings(februaryStart, 28, 2L, 90.0, 1)

        // when: 1월 월간 랭킹 집계
        val targetYearMonth = YearMonth.of(2025, 1)
        val targetYearMonthStr = ProductRankMonthly.yearMonthToString(targetYearMonth)
        val jobParameters = Properties().apply {
            setProperty("targetYearMonth", targetYearMonthStr)
            setProperty("timestamp", System.currentTimeMillis().toString())
        }

        val executionId = jobOperator.start(MonthlyRankingAggregationJobConfig.JOB_NAME, jobParameters)
        helper.getJobExecution(executionId)

        // then: 1월 데이터만 집계되어야 함
        val monthlyRankings = productRankMonthlyRepository.findTopByYearMonthOrderByRank(targetYearMonthStr, 100)
        assertThat(monthlyRankings).hasSize(1)
        assertThat(monthlyRankings[0].productId).isEqualTo(1L) // 2월 상품은 포함 안 됨
    }
}
