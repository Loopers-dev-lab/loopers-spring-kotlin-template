package com.loopers.batch.job.ranking

import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
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
@TestPropertySource(properties = ["spring.batch.job.name=${YesterdayReconciliationJobConfig.JOB_NAME}"])
@DisplayName("YesterdayReconciliationJob Integration")
class YesterdayReconciliationJobIntegrationTest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @Qualifier(YesterdayReconciliationJobConfig.JOB_NAME) private val job: Job,
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
    @DisplayName("AC-1: 기본 날짜가 어제로 설정됨")
    inner class Ac1DefaultDateIsYesterday {

        @DisplayName("baseDate 파라미터 없이 실행하면 어제 데이터를 집계한다")
        @Test
        fun shouldAggregateYesterdayData_whenNoBaseDateProvided() {
            // arrange
            val yesterday = LocalDate.now().minusDays(1)
            val today = LocalDate.now()

            // 어제 데이터
            insertHourlyMetric(toInstant(yesterday, 10), 100L, 100, 10, BigDecimal("1000"))
            insertHourlyMetric(toInstant(yesterday, 14), 100L, 50, 5, BigDecimal("500"))

            // 오늘 데이터 (포함되지 않아야 함)
            insertHourlyMetric(toInstant(today, 10), 100L, 200, 20, BigDecimal("2000"))

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

            // 어제 날짜의 일별 메트릭만 생성됨
            val yesterdayResults = queryDailyMetrics(yesterday)
            assertThat(yesterdayResults).hasSize(1)

            val product100 = yesterdayResults[0]
            assertThat(product100["view_count"]).isEqualTo(150L) // 100 + 50
            assertThat(product100["like_count"]).isEqualTo(15L) // 10 + 5
            assertThat((product100["order_amount"] as BigDecimal))
                .isEqualByComparingTo(BigDecimal("1500")) // 1000 + 500

            // 오늘 날짜의 일별 메트릭은 생성되지 않음
            val todayResults = queryDailyMetrics(today)
            assertThat(todayResults).isEmpty()
        }

        @DisplayName("명시적 baseDate 파라미터가 제공되면 해당 날짜를 사용한다")
        @Test
        fun shouldUseProvidedBaseDate_whenExplicitlySpecified() {
            // arrange
            val specificDate = LocalDate.of(2025, 1, 5)

            insertHourlyMetric(toInstant(specificDate, 10), 100L, 100, 10, BigDecimal("1000"))

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", specificDate)
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

            val results = queryDailyMetrics(specificDate)
            assertThat(results).hasSize(1)
            assertThat(results[0]["product_id"]).isEqualTo(100L)
        }
    }

    @Nested
    @DisplayName("AC-2: 멱등성 검증")
    inner class Ac2Idempotency {

        @DisplayName("두 번 실행해도 동일한 결과가 생성된다")
        @Test
        fun shouldProduceIdenticalResults_whenRunTwice() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 7)

            insertHourlyMetric(toInstant(baseDate, 10), 100L, 100, 10, BigDecimal("1000"))
            insertHourlyMetric(toInstant(baseDate, 14), 100L, 50, 5, BigDecimal("500"))
            insertHourlyMetric(toInstant(baseDate, 10), 200L, 80, 8, BigDecimal("800"))

            val jobParameters1 = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act - 첫 번째 실행
            val jobExecution1 = jobLauncherTestUtils.launchJob(jobParameters1)
            val firstRunResults = queryDailyMetrics(baseDate)

            // act - 두 번째 실행
            val jobParameters2 = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()
            val jobExecution2 = jobLauncherTestUtils.launchJob(jobParameters2)
            val secondRunResults = queryDailyMetrics(baseDate)

            // assert
            assertThat(jobExecution1.status).isEqualTo(BatchStatus.COMPLETED)
            assertThat(jobExecution2.status).isEqualTo(BatchStatus.COMPLETED)

            assertThat(secondRunResults).hasSize(firstRunResults.size)
            assertThat(secondRunResults).hasSize(2) // 중복 레코드 없음

            val product100First = firstRunResults.find { it["product_id"] == 100L }!!
            val product100Second = secondRunResults.find { it["product_id"] == 100L }!!

            assertThat(product100Second["view_count"]).isEqualTo(product100First["view_count"])
            assertThat(product100Second["like_count"]).isEqualTo(product100First["like_count"])
            assertThat((product100Second["order_amount"] as BigDecimal))
                .isEqualByComparingTo(product100First["order_amount"] as BigDecimal)
        }

        @DisplayName("늦게 도착한 데이터가 재실행 시 포함된다")
        @Test
        fun shouldIncludeLateArrivingData_whenRerun() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 7)

            // 초기 데이터
            insertHourlyMetric(toInstant(baseDate, 10), 100L, 100, 10, BigDecimal("1000"))

            val jobParameters1 = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act - 첫 번째 실행
            jobLauncherTestUtils.launchJob(jobParameters1)
            val firstRunResult = queryDailyMetrics(baseDate).find { it["product_id"] == 100L }!!
            assertThat(firstRunResult["view_count"]).isEqualTo(100L)

            // 늦게 도착한 데이터 추가 (Kafka 지연 시뮬레이션)
            insertHourlyMetric(toInstant(baseDate, 22), 100L, 50, 5, BigDecimal("500"))

            // act - 두 번째 실행 (04:00 reconciliation)
            val jobParameters2 = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()
            val jobExecution2 = jobLauncherTestUtils.launchJob(jobParameters2)

            // assert
            assertThat(jobExecution2.status).isEqualTo(BatchStatus.COMPLETED)

            val secondRunResult = queryDailyMetrics(baseDate).find { it["product_id"] == 100L }!!
            assertThat(secondRunResult["view_count"]).isEqualTo(150L) // 100 + 50
            assertThat(secondRunResult["like_count"]).isEqualTo(15L) // 10 + 5
            assertThat((secondRunResult["order_amount"] as BigDecimal))
                .isEqualByComparingTo(BigDecimal("1500")) // 1000 + 500
        }
    }

    @Nested
    @DisplayName("AC-3: 오늘 데이터 격리")
    inner class Ac3TodayIsolation {

        @DisplayName("오늘의 메트릭에는 영향을 주지 않는다")
        @Test
        fun shouldNotAffectTodayMetrics() {
            // arrange
            val yesterday = LocalDate.of(2025, 1, 7)
            val today = LocalDate.of(2025, 1, 8)

            // 어제 데이터
            insertHourlyMetric(toInstant(yesterday, 10), 100L, 100, 10, BigDecimal("1000"))

            // 오늘 데이터
            insertHourlyMetric(toInstant(today, 10), 100L, 200, 20, BigDecimal("2000"))
            insertHourlyMetric(toInstant(today, 14), 200L, 300, 30, BigDecimal("3000"))

            // 오늘 일별 메트릭이 이미 존재 (TodayDailyRollupJob에 의해 생성됨)
            insertDailyMetric(today, 100L, 200, 20, BigDecimal("2000"))
            insertDailyMetric(today, 200L, 300, 30, BigDecimal("3000"))

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", yesterday)
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

            // 어제 메트릭이 생성됨
            val yesterdayResults = queryDailyMetrics(yesterday)
            assertThat(yesterdayResults).hasSize(1)
            assertThat(yesterdayResults[0]["product_id"]).isEqualTo(100L)
            assertThat(yesterdayResults[0]["view_count"]).isEqualTo(100L)

            // 오늘 메트릭은 변경되지 않음
            val todayResults = queryDailyMetrics(today)
            assertThat(todayResults).hasSize(2)

            val todayProduct100 = todayResults.find { it["product_id"] == 100L }!!
            assertThat(todayProduct100["view_count"]).isEqualTo(200L) // 변경 없음

            val todayProduct200 = todayResults.find { it["product_id"] == 200L }!!
            assertThat(todayProduct200["view_count"]).isEqualTo(300L) // 변경 없음
        }
    }

    @Nested
    @DisplayName("개별 Step 테스트")
    inner class IndividualStepTest {

        @DisplayName("yesterdayReconciliationStep이 시간별 메트릭을 일별로 집계한다")
        @Test
        fun shouldAggregateYesterdayMetrics() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 7)

            insertHourlyMetric(toInstant(baseDate, 10), 100L, 10, 1, BigDecimal("100"))
            insertHourlyMetric(toInstant(baseDate, 14), 100L, 20, 2, BigDecimal("200"))
            insertHourlyMetric(toInstant(baseDate, 22), 100L, 30, 3, BigDecimal("300"))

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val stepExecution = jobLauncherTestUtils.launchStep(
                "yesterdayReconciliationStep",
                jobParameters,
            )

            // assert
            assertThat(stepExecution.status).isEqualTo(BatchStatus.COMPLETED)

            val results = queryDailyMetrics(baseDate)
            assertThat(results).hasSize(1)

            val product100 = results[0]
            assertThat(product100["view_count"]).isEqualTo(60L) // 10 + 20 + 30
            assertThat(product100["like_count"]).isEqualTo(6L) // 1 + 2 + 3
            assertThat((product100["order_amount"] as BigDecimal))
                .isEqualByComparingTo(BigDecimal("600")) // 100 + 200 + 300
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

    private fun insertDailyMetric(
        statDate: LocalDate,
        productId: Long,
        viewCount: Long,
        likeCount: Long,
        orderAmount: BigDecimal,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO product_daily_metric (stat_date, product_id, view_count, like_count, order_amount, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, NOW(), NOW())
            """.trimIndent(),
            Date.valueOf(statDate),
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
