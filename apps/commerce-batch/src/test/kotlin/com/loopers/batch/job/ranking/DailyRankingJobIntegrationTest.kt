package com.loopers.batch.job.ranking

import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
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
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.sql.Date
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@SpringBootTest
@SpringBatchTest
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.batch.job.name=${DailyRankingJobConfig.JOB_NAME}"])
@DisplayName("DailyRankingJob Integration")
class DailyRankingJobIntegrationTest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @Qualifier(DailyRankingJobConfig.JOB_NAME) private val job: Job,
    private val jdbcTemplate: JdbcTemplate,
    private val redisTemplate: RedisTemplate<String, String>,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @BeforeEach
    fun setUp() {
        jobLauncherTestUtils.job = job
        // DB 초기화
        databaseCleanUp.afterPropertiesSet()
        databaseCleanUp.truncateAllTables()
        // Redis 초기화
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
    }

    @Nested
    @DisplayName("AC-1: 오늘의 일별 메트릭이 감쇠 가중치와 함께 랭킹에 반영된다")
    inner class Ac1TodayMetricsWithDecay {

        @DisplayName("오늘과 어제 메트릭이 모두 있을 때 감쇠 공식이 적용된다")
        @Test
        fun shouldApplyDecayFormula_whenTodayAndYesterdayMetricsExist() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val today = baseDate
            val yesterday = baseDate.minusDays(1)

            // 상품 100: 오늘 + 어제 데이터
            // 오늘: 100*0.1 + 10*0.2 + 1000*0.6 = 612 * 0.9 = 550.8
            // 어제: 80*0.1 + 8*0.2 + 800*0.6 = 489.6 * 0.1 = 48.96
            // 합계: 550.8 + 48.96 = 599.76
            insertMetric(today, 100L, 100, 10, BigDecimal("1000"))
            insertMetric(yesterday, 100L, 80, 8, BigDecimal("800"))

            // 상품 200: 오늘만
            // 오늘: 50*0.1 + 5*0.2 + 500*0.6 = 306 * 0.9 = 275.4
            insertMetric(today, 200L, 50, 5, BigDecimal("500"))

            val jobParameters = jobLauncherTestUtils.uniqueJobParametersBuilder
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)
            assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)

            val stagingKey = "ranking:products:daily:20250108:staging"
            val zSetOps = redisTemplate.opsForZSet()

            val score100 = zSetOps.score(stagingKey, "100")
            val score200 = zSetOps.score(stagingKey, "200")

            assertThat(score100).isCloseTo(599.76, Offset.offset(0.01))
            assertThat(score200).isCloseTo(275.4, Offset.offset(0.01))
        }

        @DisplayName("Job이 성공적으로 완료되면 Step이 1개만 실행된다")
        @Test
        fun shouldExecuteSingleStep_whenJobCompletes() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            insertMetric(baseDate, 100L, 100, 10, BigDecimal("1000"))

            val jobParameters = jobLauncherTestUtils.uniqueJobParametersBuilder
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.stepExecutions).hasSize(1)
            assertThat(jobExecution.stepExecutions.first().stepName)
                .isEqualTo("dailyMetricAggregationStep")
        }
    }

    @Nested
    @DisplayName("AC-2: 어제에만 활동한 상품이 감쇠된 점수로 나타난다")
    inner class Ac2YesterdayOnlyProducts {

        @DisplayName("어제에만 활동한 상품도 감쇠된 점수로 랭킹에 포함된다")
        @Test
        fun shouldIncludeYesterdayOnlyProducts_withDecayedScores() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val today = baseDate
            val yesterday = baseDate.minusDays(1)

            // 상품 100: 어제에만 존재
            // 어제: 100*0.1 + 10*0.2 + 1000*0.6 = 612 * 0.1 = 61.2
            insertMetric(yesterday, 100L, 100, 10, BigDecimal("1000"))

            // 상품 200: 오늘에만 존재
            // 오늘: 50*0.1 + 5*0.2 + 500*0.6 = 306 * 0.9 = 275.4
            insertMetric(today, 200L, 50, 5, BigDecimal("500"))

            val jobParameters = jobLauncherTestUtils.uniqueJobParametersBuilder
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

            val stagingKey = "ranking:products:daily:20250108:staging"
            val zSetOps = redisTemplate.opsForZSet()

            // 두 상품 모두 랭킹에 포함
            val size = zSetOps.size(stagingKey)
            assertThat(size).isEqualTo(2L)

            // 점수 확인
            val score100 = zSetOps.score(stagingKey, "100")
            val score200 = zSetOps.score(stagingKey, "200")

            assertThat(score100).isCloseTo(61.2, Offset.offset(0.01))
            assertThat(score200).isCloseTo(275.4, Offset.offset(0.01))

            // 상품 200이 더 높은 순위 (감쇠 적용 결과)
            assertThat(score200).isGreaterThan(score100)
        }

        @DisplayName("오늘 메트릭은 0.9, 어제 메트릭은 0.1 가중치를 받는다")
        @Test
        fun shouldApplyCorrectDecayWeights() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val today = baseDate
            val yesterday = baseDate.minusDays(1)

            // 동일한 메트릭 값으로 두 날짜에 데이터 삽입
            // 기본 점수: 100*0.1 + 10*0.2 + 1000*0.6 = 612
            insertMetric(today, 100L, 100, 10, BigDecimal("1000"))
            insertMetric(yesterday, 200L, 100, 10, BigDecimal("1000"))

            val jobParameters = jobLauncherTestUtils.uniqueJobParametersBuilder
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

            val stagingKey = "ranking:products:daily:20250108:staging"
            val zSetOps = redisTemplate.opsForZSet()

            val score100 = zSetOps.score(stagingKey, "100") // 오늘: 612 * 0.9 = 550.8
            val score200 = zSetOps.score(stagingKey, "200") // 어제: 612 * 0.1 = 61.2

            assertThat(score100).isCloseTo(550.8, Offset.offset(0.01))
            assertThat(score200).isCloseTo(61.2, Offset.offset(0.01))

            // 가중치 비율 확인 (0.9 : 0.1 = 9 : 1)
            val ratio = score100!! / score200!!
            assertThat(ratio).isCloseTo(9.0, Offset.offset(0.01))
        }
    }

    @Nested
    @DisplayName("AC-3: 과거 날짜로 Job을 재실행할 수 있다")
    inner class Ac3HistoricalDateRerun {

        @DisplayName("과거 날짜를 파라미터로 전달하면 해당 날짜 기준으로 집계한다")
        @Test
        fun shouldAggregateForHistoricalDate_whenParameterProvided() {
            // arrange
            val historicalDate = LocalDate.of(2025, 1, 5)
            val previousDay = historicalDate.minusDays(1)

            insertMetric(historicalDate, 100L, 100, 10, BigDecimal("1000"))
            insertMetric(previousDay, 200L, 50, 5, BigDecimal("500"))

            val jobParameters = jobLauncherTestUtils.uniqueJobParametersBuilder
                .addLocalDate("baseDate", historicalDate)
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

            // 과거 날짜 기준 Redis 키 확인
            val stagingKey = "ranking:products:daily:20250105:staging"
            val zSetOps = redisTemplate.opsForZSet()

            assertThat(zSetOps.size(stagingKey)).isEqualTo(2L)

            // 상품 100: 오늘(historicalDate) 기준 0.9 가중치
            val score100 = zSetOps.score(stagingKey, "100")
            assertThat(score100).isCloseTo(550.8, Offset.offset(0.01))

            // 상품 200: 어제(previousDay) 기준 0.1 가중치
            val score200 = zSetOps.score(stagingKey, "200")
            assertThat(score200).isCloseTo(30.6, Offset.offset(0.01))
        }

        @DisplayName("동일한 날짜로 재실행해도 정상 동작한다 (멱등성)")
        @Test
        fun shouldBeIdempotent_whenRerunWithSameDate() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            insertMetric(baseDate, 100L, 100, 10, BigDecimal("1000"))

            // 첫 번째 실행
            val jobParameters1 = jobLauncherTestUtils.uniqueJobParametersBuilder
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            val jobExecution1 = jobLauncherTestUtils.launchJob(jobParameters1)
            assertThat(jobExecution1.status).isEqualTo(BatchStatus.COMPLETED)

            // 두 번째 실행 (다른 JobInstance를 위해 unique 파라미터 추가)
            val jobParameters2 = jobLauncherTestUtils.uniqueJobParametersBuilder
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val jobExecution2 = jobLauncherTestUtils.launchJob(jobParameters2)

            // assert
            assertThat(jobExecution2.status).isEqualTo(BatchStatus.COMPLETED)

            // Redis에 점수가 누적됨 (ZINCRBY 특성)
            val stagingKey = "ranking:products:daily:20250108:staging"
            val zSetOps = redisTemplate.opsForZSet()

            // 두 번 실행으로 점수가 2배가 됨 (550.8 * 2 = 1101.6)
            val score100 = zSetOps.score(stagingKey, "100")
            assertThat(score100).isCloseTo(1101.6, Offset.offset(0.01))
        }
    }

    @Nested
    @DisplayName("메트릭 데이터가 없는 경우")
    inner class NoMetrics {

        @DisplayName("쓰기 없이 Job이 성공적으로 완료된다")
        @Test
        fun shouldCompleteWithZeroWrites_whenNoMetrics() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val jobParameters = jobLauncherTestUtils.uniqueJobParametersBuilder
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)
            assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)

            // Redis에 데이터가 없음
            val stagingKey = "ranking:products:daily:20250108:staging"
            val exists = redisTemplate.hasKey(stagingKey)
            assertThat(exists).isFalse()
        }
    }

    @Nested
    @DisplayName("Redis 스테이징 키 TTL")
    inner class RedisTtl {

        @DisplayName("Redis 스테이징 키가 24시간 TTL로 생성된다")
        @Test
        fun shouldCreateStagingKeyWith24HourTtl() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            insertMetric(baseDate, 100L, 100, 10, BigDecimal("1000"))

            val jobParameters = jobLauncherTestUtils.uniqueJobParametersBuilder
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

            val stagingKey = "ranking:products:daily:20250108:staging"
            val ttl = redisTemplate.getExpire(stagingKey, TimeUnit.HOURS)

            // TTL이 24시간 이하이면서 23시간 이상 (테스트 실행 시간 고려)
            assertThat(ttl).isBetween(23L, 24L)
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
            val yesterday = baseDate.minusDays(1)

            // 3개 레코드 삽입
            insertMetric(baseDate, 100L, 100, 10, BigDecimal("1000"))
            insertMetric(baseDate, 200L, 50, 5, BigDecimal("500"))
            insertMetric(yesterday, 100L, 80, 8, BigDecimal("800"))

            val jobParameters = jobLauncherTestUtils.uniqueJobParametersBuilder
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            val stepExecution = jobExecution.stepExecutions.first()

            // 3개 읽음
            assertThat(stepExecution.readCount).isEqualTo(3)
            // 3개 쓰기 (Redis에 ZINCRBY)
            assertThat(stepExecution.writeCount).isEqualTo(3)
            // 스킵 없음
            assertThat(stepExecution.skipCount).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("개별 Step 테스트")
    inner class IndividualStepTest {

        @DisplayName("dailyMetricAggregationStep이 메트릭을 Redis에 집계한다")
        @Test
        fun shouldAggregateMetricsToRedis() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val stagingKey = "ranking:products:daily:20250108:staging"

            insertMetric(baseDate, 100L, 100, 10, BigDecimal("1000"))
            insertMetric(baseDate, 200L, 50, 5, BigDecimal("500"))

            val jobParameters = jobLauncherTestUtils.uniqueJobParametersBuilder
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val stepExecution = jobLauncherTestUtils.launchStep(
                "dailyMetricAggregationStep",
                jobParameters,
            )

            // assert
            assertThat(stepExecution.status).isEqualTo(BatchStatus.COMPLETED)

            val zSetOps = redisTemplate.opsForZSet()
            val score100 = zSetOps.score(stagingKey, "100")
            val score200 = zSetOps.score(stagingKey, "200")

            // 상품 100: 612 * 0.9 = 550.8
            assertThat(score100).isCloseTo(550.8, Offset.offset(0.01))
            // 상품 200: 306 * 0.9 = 275.4
            assertThat(score200).isCloseTo(275.4, Offset.offset(0.01))
        }
    }

    private fun insertMetric(
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
}
