package com.loopers.batch.job.ranking

import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
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
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

@SpringBootTest
@SpringBatchTest
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.batch.job.name=${HourlyRankingJobConfig.JOB_NAME}"])
@DisplayName("HourlyRankingJob Integration")
class HourlyRankingJobIntegrationTest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @Qualifier(HourlyRankingJobConfig.JOB_NAME) private val job: Job,
    private val jdbcTemplate: JdbcTemplate,
    private val redisTemplate: RedisTemplate<String, String>,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    companion object {
        private val ZONE_ID = ZoneId.of("Asia/Seoul")
    }

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
    @DisplayName("AC-1: 메트릭 데이터가 있는 경우")
    inner class Ac1MetricsExist {

        @DisplayName("Job이 성공적으로 완료된다")
        @Test
        fun shouldCompleteSuccessfully_whenMetricsExist() {
            // arrange
            val baseDateTime = Instant.parse("2025-01-08T05:30:00Z") // UTC (KST 14:30)
            val currentHour = baseDateTime.truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)

            // 현재 시간 버킷 데이터
            insertMetric(currentHour, 100L, 100, 10, BigDecimal("1000"))
            insertMetric(currentHour, 200L, 50, 5, BigDecimal("500"))

            // 이전 시간 버킷 데이터
            insertMetric(previousHour, 100L, 80, 8, BigDecimal("800"))

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addString("baseDateTime", baseDateTime.toString())
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)
            assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)

            // Step 실행 확인 (1개 Step)
            assertThat(jobExecution.stepExecutions).hasSize(1)

            // Redis에 데이터가 저장되었는지 확인
            val stagingKey = "ranking:products:hourly:2025010814:staging"
            val zSetOps = redisTemplate.opsForZSet()
            val size = zSetOps.size(stagingKey)
            assertThat(size).isEqualTo(2L) // 상품 100, 200
        }

        @DisplayName("현재 시간과 이전 시간 데이터를 모두 집계한다")
        @Test
        fun shouldAggregateCurrentAndPreviousHourData() {
            // arrange
            val baseDateTime = Instant.parse("2025-01-08T05:30:00Z")
            val currentHour = baseDateTime.truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)

            // 상품 100: 현재 + 이전 시간 데이터
            // 현재: 100*0.1 + 10*0.2 + 1000*0.6 = 612 * 0.9 = 550.8
            // 이전: 80*0.1 + 8*0.2 + 800*0.6 = 489.6 * 0.1 = 48.96
            // 합계: 550.8 + 48.96 = 599.76
            insertMetric(currentHour, 100L, 100, 10, BigDecimal("1000"))
            insertMetric(previousHour, 100L, 80, 8, BigDecimal("800"))

            // 상품 200: 현재 시간만
            // 현재: 50*0.1 + 5*0.2 + 500*0.6 = 306 * 0.9 = 275.4
            insertMetric(currentHour, 200L, 50, 5, BigDecimal("500"))

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addString("baseDateTime", baseDateTime.toString())
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

            val stagingKey = "ranking:products:hourly:2025010814:staging"
            val zSetOps = redisTemplate.opsForZSet()

            val score100 = zSetOps.score(stagingKey, "100")
            val score200 = zSetOps.score(stagingKey, "200")

            assertThat(score100).isCloseTo(599.76, org.assertj.core.data.Offset.offset(0.01))
            assertThat(score200).isCloseTo(275.4, org.assertj.core.data.Offset.offset(0.01))
        }
    }

    @Nested
    @DisplayName("AC-2: 메트릭 데이터가 없는 경우")
    inner class Ac2NoMetrics {

        @DisplayName("쓰기 없이 Job이 성공적으로 완료된다")
        @Test
        fun shouldCompleteWithZeroWrites_whenNoMetrics() {
            // arrange
            val baseDateTime = Instant.parse("2025-01-08T05:30:00Z")
            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addString("baseDateTime", baseDateTime.toString())
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)
            assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)

            // Redis에 데이터가 없음
            val stagingKey = "ranking:products:hourly:2025010814:staging"
            val exists = redisTemplate.hasKey(stagingKey)
            assertThat(exists).isFalse()
        }
    }

    @Nested
    @DisplayName("AC-3: 이전 시간 버킷의 상품")
    inner class Ac3PreviousHourOnly {

        @DisplayName("이전 시간에만 활동한 상품도 감쇠된 점수로 랭킹에 포함된다")
        @Test
        fun shouldIncludeProductsWithDecayedScores_whenOnlyPreviousHour() {
            // arrange
            val baseDateTime = Instant.parse("2025-01-08T05:30:00Z")
            val currentHour = baseDateTime.truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)

            // 상품 100: 이전 시간에만 존재
            // 이전: 100*0.1 + 10*0.2 + 1000*0.6 = 612 * 0.1 = 61.2
            insertMetric(previousHour, 100L, 100, 10, BigDecimal("1000"))

            // 상품 200: 현재 시간에만 존재
            // 현재: 50*0.1 + 5*0.2 + 500*0.6 = 306 * 0.9 = 275.4
            insertMetric(currentHour, 200L, 50, 5, BigDecimal("500"))

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addString("baseDateTime", baseDateTime.toString())
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

            val stagingKey = "ranking:products:hourly:2025010814:staging"
            val zSetOps = redisTemplate.opsForZSet()

            // 두 상품 모두 랭킹에 포함
            val size = zSetOps.size(stagingKey)
            assertThat(size).isEqualTo(2L)

            // 점수 확인
            val score100 = zSetOps.score(stagingKey, "100")
            val score200 = zSetOps.score(stagingKey, "200")

            assertThat(score100).isCloseTo(61.2, org.assertj.core.data.Offset.offset(0.01))
            assertThat(score200).isCloseTo(275.4, org.assertj.core.data.Offset.offset(0.01))

            // 상품 200이 더 높은 순위 (감쇠 적용 결과)
            assertThat(score200).isGreaterThan(score100)
        }
    }

    @Nested
    @DisplayName("AC-4: Redis 스테이징 키 TTL")
    inner class Ac4RedisTtl {

        @DisplayName("Redis 스테이징 키가 24시간 TTL로 생성된다")
        @Test
        fun shouldCreateStagingKeyWith24HourTtl() {
            // arrange
            val baseDateTime = Instant.parse("2025-01-08T05:30:00Z")
            val currentHour = baseDateTime.truncatedTo(ChronoUnit.HOURS)

            insertMetric(currentHour, 100L, 100, 10, BigDecimal("1000"))

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addString("baseDateTime", baseDateTime.toString())
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

            val stagingKey = "ranking:products:hourly:2025010814:staging"
            val ttl = redisTemplate.getExpire(stagingKey, TimeUnit.HOURS)

            // TTL이 24시간 이하이면서 23시간 이상 (테스트 실행 시간 고려)
            assertThat(ttl).isBetween(23L, 24L)
        }
    }

    @Nested
    @DisplayName("AC-5: 실행 기록")
    inner class Ac5ExecutionRecord {

        @DisplayName("읽기/쓰기 횟수가 정확하게 기록된다")
        @Test
        fun shouldRecordAccurateReadWriteCounts() {
            // arrange
            val baseDateTime = Instant.parse("2025-01-08T05:30:00Z")
            val currentHour = baseDateTime.truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)

            // 3개 레코드 삽입
            insertMetric(currentHour, 100L, 100, 10, BigDecimal("1000"))
            insertMetric(currentHour, 200L, 50, 5, BigDecimal("500"))
            insertMetric(previousHour, 100L, 80, 8, BigDecimal("800"))

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addString("baseDateTime", baseDateTime.toString())
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
    @DisplayName("감쇠 가중치 검증")
    inner class DecayWeightVerification {

        @DisplayName("현재 시간 메트릭은 0.9, 이전 시간 메트릭은 0.1 가중치를 받는다")
        @Test
        fun shouldApplyCorrectDecayWeights() {
            // arrange
            val baseDateTime = Instant.parse("2025-01-08T05:30:00Z")
            val currentHour = baseDateTime.truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)

            // 동일한 메트릭 값으로 두 시간 버킷에 데이터 삽입
            // 기본 점수: 100*0.1 + 10*0.2 + 1000*0.6 = 612
            insertMetric(currentHour, 100L, 100, 10, BigDecimal("1000"))
            insertMetric(previousHour, 200L, 100, 10, BigDecimal("1000"))

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addString("baseDateTime", baseDateTime.toString())
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

            val stagingKey = "ranking:products:hourly:2025010814:staging"
            val zSetOps = redisTemplate.opsForZSet()

            val score100 = zSetOps.score(stagingKey, "100") // 현재 시간: 612 * 0.9 = 550.8
            val score200 = zSetOps.score(stagingKey, "200") // 이전 시간: 612 * 0.1 = 61.2

            assertThat(score100).isCloseTo(550.8, org.assertj.core.data.Offset.offset(0.01))
            assertThat(score200).isCloseTo(61.2, org.assertj.core.data.Offset.offset(0.01))

            // 가중치 비율 확인 (0.9 : 0.1 = 9 : 1)
            val ratio = score100!! / score200!!
            assertThat(ratio).isCloseTo(9.0, org.assertj.core.data.Offset.offset(0.01))
        }
    }

    @Nested
    @DisplayName("개별 Step 테스트")
    inner class IndividualStepTest {

        @DisplayName("hourlyMetricAggregationStep이 메트릭을 Redis에 집계한다")
        @Test
        fun shouldAggregateMetricsToRedis() {
            // arrange
            val baseDateTime = Instant.parse("2025-01-08T05:30:00Z")
            val currentHour = baseDateTime.truncatedTo(ChronoUnit.HOURS)
            val stagingKey = "ranking:products:hourly:2025010814:staging"

            insertMetric(currentHour, 100L, 100, 10, BigDecimal("1000"))
            insertMetric(currentHour, 200L, 50, 5, BigDecimal("500"))

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addString("baseDateTime", baseDateTime.toString())
                .toJobParameters()

            // act
            val stepExecution = jobLauncherTestUtils.launchStep(
                "hourlyMetricAggregationStep",
                jobParameters,
            )

            // assert
            assertThat(stepExecution.status).isEqualTo(BatchStatus.COMPLETED)

            val zSetOps = redisTemplate.opsForZSet()
            val score100 = zSetOps.score(stagingKey, "100")
            val score200 = zSetOps.score(stagingKey, "200")

            // 상품 100: 612 * 0.9 = 550.8
            assertThat(score100).isCloseTo(550.8, org.assertj.core.data.Offset.offset(0.01))
            // 상품 200: 306 * 0.9 = 275.4
            assertThat(score200).isCloseTo(275.4, org.assertj.core.data.Offset.offset(0.01))
        }
    }

    private fun insertMetric(
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
}
