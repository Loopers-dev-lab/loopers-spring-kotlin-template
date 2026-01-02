package com.loopers.batch.job.ranking

import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.batch.job.name=${TodayDailyRollupJobConfig.JOB_NAME}"])
@DisplayName("TodayDailyRollupJob Integration")
class TodayDailyRollupJobIntegrationTest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @Qualifier(TodayDailyRollupJobConfig.JOB_NAME) private val job: Job,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    companion object {
        private val ZONE_ID = ZoneId.of("Asia/Seoul")
    }

    @BeforeEach
    fun setUp() {
        jobLauncherTestUtils.job = job
        databaseCleanUp.afterPropertiesSet()
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("AC-1: 멱등성 검증")
    inner class Ac1Idempotency {

        @DisplayName("두 번 실행해도 동일한 결과가 생성된다")
        @Test
        fun shouldProduceIdenticalResults_whenRunTwice() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)

            // 시간별 메트릭 삽입
            insertHourlyMetric(toInstant(baseDate, 10), 100L, 100, 10, BigDecimal("1000"))
            insertHourlyMetric(toInstant(baseDate, 14), 100L, 50, 5, BigDecimal("500"))
            insertHourlyMetric(toInstant(baseDate, 10), 200L, 80, 8, BigDecimal("800"))

            val jobParameters1 = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act - 첫 번째 실행
            val jobExecution1 = jobLauncherTestUtils.launchJob(jobParameters1)

            // 첫 번째 실행 결과 저장
            val firstRunResults = queryDailyMetrics(baseDate)

            // act - 두 번째 실행 (새로운 job parameters 필요)
            val jobParameters2 = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()
            val jobExecution2 = jobLauncherTestUtils.launchJob(jobParameters2)

            // 두 번째 실행 결과
            val secondRunResults = queryDailyMetrics(baseDate)

            // assert
            assertThat(jobExecution1.status).isEqualTo(BatchStatus.COMPLETED)
            assertThat(jobExecution2.status).isEqualTo(BatchStatus.COMPLETED)

            // 결과가 동일해야 함 (레코드 수)
            assertThat(secondRunResults).hasSize(firstRunResults.size)

            // 각 상품의 값이 동일해야 함
            val product100First = firstRunResults.find { it["product_id"] == 100L }!!
            val product100Second = secondRunResults.find { it["product_id"] == 100L }!!

            assertThat(product100Second["view_count"]).isEqualTo(product100First["view_count"])
            assertThat(product100Second["like_count"]).isEqualTo(product100First["like_count"])
            assertThat((product100Second["order_amount"] as BigDecimal))
                .isEqualByComparingTo(product100First["order_amount"] as BigDecimal)

            // 중복 레코드가 없어야 함
            assertThat(secondRunResults).hasSize(2)
        }
    }

    @Nested
    @DisplayName("AC-2: 늦게 도착한 데이터 포함")
    inner class Ac2LateArrivingData {

        @DisplayName("Job 실행 후 도착한 데이터가 다음 실행에 포함된다")
        @Test
        fun shouldIncludeLateArrivingData_whenJobRunsAfterArrival() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)

            // 초기 데이터
            insertHourlyMetric(toInstant(baseDate, 10), 100L, 100, 10, BigDecimal("1000"))

            val jobParameters1 = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act - 첫 번째 실행
            val jobExecution1 = jobLauncherTestUtils.launchJob(jobParameters1)

            // 첫 번째 실행 결과 확인
            val firstRunResult = queryDailyMetrics(baseDate).find { it["product_id"] == 100L }!!
            assertThat(firstRunResult["view_count"]).isEqualTo(100L)

            // 늦게 도착한 데이터 추가
            insertHourlyMetric(toInstant(baseDate, 14), 100L, 50, 5, BigDecimal("500"))

            // act - 두 번째 실행
            val jobParameters2 = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()
            val jobExecution2 = jobLauncherTestUtils.launchJob(jobParameters2)

            // assert
            assertThat(jobExecution1.status).isEqualTo(BatchStatus.COMPLETED)
            assertThat(jobExecution2.status).isEqualTo(BatchStatus.COMPLETED)

            // 두 번째 실행 결과에 늦게 도착한 데이터가 포함됨
            val secondRunResult = queryDailyMetrics(baseDate).find { it["product_id"] == 100L }!!
            assertThat(secondRunResult["view_count"]).isEqualTo(150L) // 100 + 50
            assertThat(secondRunResult["like_count"]).isEqualTo(15L) // 10 + 5
            assertThat((secondRunResult["order_amount"] as BigDecimal))
                .isEqualByComparingTo(BigDecimal("1500")) // 1000 + 500
        }
    }

    @Nested
    @DisplayName("AC-3: 시간별 메트릭이 없는 경우")
    inner class Ac3NoHourlyMetrics {

        @DisplayName("쓰기 없이 Job이 성공적으로 완료된다")
        @Test
        fun shouldCompleteWithZeroWrites_whenNoHourlyMetrics() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)
            assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)

            // Step 실행 확인
            assertThat(jobExecution.stepExecutions).hasSize(1)

            val stepExecution = jobExecution.stepExecutions.first()
            assertThat(stepExecution.readCount).isEqualTo(0)
            assertThat(stepExecution.writeCount).isEqualTo(0)

            // 일별 메트릭 테이블에 데이터가 없음
            val results = queryDailyMetrics(baseDate)
            assertThat(results).isEmpty()
        }
    }

    @Nested
    @DisplayName("AC-4: 집계 정확성")
    inner class Ac4CorrectAggregation {

        @DisplayName("view_count, like_count, order_amount를 정확하게 합산한다")
        @Test
        fun shouldCorrectlySumAllMetrics() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)

            // 상품 100: 여러 시간대에 걸쳐 데이터
            insertHourlyMetric(toInstant(baseDate, 10), 100L, 10, 1, BigDecimal("100.50"))
            insertHourlyMetric(toInstant(baseDate, 11), 100L, 20, 2, BigDecimal("200.25"))
            insertHourlyMetric(toInstant(baseDate, 14), 100L, 30, 3, BigDecimal("300.75"))

            // 상품 200: 단일 시간대 데이터
            insertHourlyMetric(toInstant(baseDate, 15), 200L, 50, 5, BigDecimal("500.00"))

            // 상품 300: 음수 값 포함 (좋아요 취소)
            insertHourlyMetric(toInstant(baseDate, 16), 300L, 40, 10, BigDecimal("400.00"))
            insertHourlyMetric(toInstant(baseDate, 17), 300L, 10, -3, BigDecimal("100.00"))

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

            val results = queryDailyMetrics(baseDate)
            assertThat(results).hasSize(3)

            // 상품 100 검증
            val product100 = results.find { it["product_id"] == 100L }!!
            assertThat(product100["view_count"]).isEqualTo(60L) // 10 + 20 + 30
            assertThat(product100["like_count"]).isEqualTo(6L) // 1 + 2 + 3
            assertThat((product100["order_amount"] as BigDecimal))
                .isEqualByComparingTo(BigDecimal("601.50")) // 100.50 + 200.25 + 300.75

            // 상품 200 검증
            val product200 = results.find { it["product_id"] == 200L }!!
            assertThat(product200["view_count"]).isEqualTo(50L)
            assertThat(product200["like_count"]).isEqualTo(5L)
            assertThat((product200["order_amount"] as BigDecimal))
                .isEqualByComparingTo(BigDecimal("500.00"))

            // 상품 300 검증 (음수 값 포함)
            val product300 = results.find { it["product_id"] == 300L }!!
            assertThat(product300["view_count"]).isEqualTo(50L) // 40 + 10
            assertThat(product300["like_count"]).isEqualTo(7L) // 10 + (-3)
            assertThat((product300["order_amount"] as BigDecimal))
                .isEqualByComparingTo(BigDecimal("500.00")) // 400 + 100
        }

        @DisplayName("다른 날짜의 데이터는 집계에 포함되지 않는다")
        @Test
        fun shouldNotIncludeOtherDatesInAggregation() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val yesterday = baseDate.minusDays(1)
            val tomorrow = baseDate.plusDays(1)

            // baseDate 데이터
            insertHourlyMetric(toInstant(baseDate, 10), 100L, 100, 10, BigDecimal("1000"))

            // 어제 데이터 (포함되지 않아야 함)
            insertHourlyMetric(toInstant(yesterday, 23), 100L, 50, 5, BigDecimal("500"))

            // 내일 데이터 (포함되지 않아야 함)
            insertHourlyMetric(toInstant(tomorrow, 0), 100L, 60, 6, BigDecimal("600"))

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

            val results = queryDailyMetrics(baseDate)
            assertThat(results).hasSize(1)

            val product100 = results[0]
            assertThat(product100["view_count"]).isEqualTo(100L) // baseDate 데이터만
            assertThat(product100["like_count"]).isEqualTo(10L)
            assertThat((product100["order_amount"] as BigDecimal))
                .isEqualByComparingTo(BigDecimal("1000"))
        }
    }

    @Nested
    @DisplayName("실행 기록")
    inner class ExecutionRecord {

        @DisplayName("읽기/쓰기 횟수가 정확하게 기록된다")
        @Test
        fun shouldRecordAccurateReadWriteCounts() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)

            // 3개 상품, 각각 여러 시간대 데이터
            insertHourlyMetric(toInstant(baseDate, 10), 100L, 10, 1, BigDecimal("100"))
            insertHourlyMetric(toInstant(baseDate, 11), 100L, 20, 2, BigDecimal("200"))
            insertHourlyMetric(toInstant(baseDate, 10), 200L, 30, 3, BigDecimal("300"))
            insertHourlyMetric(toInstant(baseDate, 10), 300L, 40, 4, BigDecimal("400"))

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            val stepExecution = jobExecution.stepExecutions.first()

            // Reader는 GROUP BY 결과를 읽으므로 3개 읽음 (상품별)
            assertThat(stepExecution.readCount).isEqualTo(3)
            // 3개 쓰기 (UPSERT)
            assertThat(stepExecution.writeCount).isEqualTo(3)
            // 스킵 없음
            assertThat(stepExecution.skipCount).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("개별 Step 테스트")
    inner class IndividualStepTest {

        @DisplayName("dailyRollupStep이 시간별 메트릭을 일별로 집계한다")
        @Test
        fun shouldAggregateDailyMetrics() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)

            insertHourlyMetric(toInstant(baseDate, 10), 100L, 10, 1, BigDecimal("100"))
            insertHourlyMetric(toInstant(baseDate, 14), 100L, 20, 2, BigDecimal("200"))

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val stepExecution = jobLauncherTestUtils.launchStep(
                "todayDailyRollupStep",
                jobParameters,
            )

            // assert
            assertThat(stepExecution.status).isEqualTo(BatchStatus.COMPLETED)

            val results = queryDailyMetrics(baseDate)
            assertThat(results).hasSize(1)

            val product100 = results[0]
            assertThat(product100["view_count"]).isEqualTo(30L)
            assertThat(product100["like_count"]).isEqualTo(3L)
            assertThat((product100["order_amount"] as BigDecimal))
                .isEqualByComparingTo(BigDecimal("300"))
        }
    }

    private fun toInstant(date: LocalDate, hour: Int): Instant {
        return date.atTime(hour, 0).atZone(ZONE_ID).toInstant()
    }

    private fun insertHourlyMetric(
        statHour: Instant,
        productId: Long,
        viewCount: Long,
        likeCount: Long,
        orderAmount: BigDecimal,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO product_hourly_metric (stat_hour, product_id, view_count, like_count, order_amount, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, NOW(), NOW())
            """.trimIndent(),
            Timestamp.from(statHour),
            productId,
            viewCount,
            likeCount,
            orderAmount,
        )
    }

    private fun queryDailyMetrics(statDate: LocalDate): List<Map<String, Any>> {
        return jdbcTemplate.queryForList(
            """
            SELECT stat_date, product_id, view_count, like_count, order_amount
            FROM product_daily_metric
            WHERE stat_date = ?
            ORDER BY product_id
            """.trimIndent(),
            Date.valueOf(statDate),
        )
    }
}
