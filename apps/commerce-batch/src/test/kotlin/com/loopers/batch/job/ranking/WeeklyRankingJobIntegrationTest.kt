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
import java.sql.Date
import java.time.LocalDate

@SpringBootTest
@SpringBatchTest
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.batch.job.name=${WeeklyRankingJobConfig.JOB_NAME}"])
@DisplayName("WeeklyRankingJob Integration")
class WeeklyRankingJobIntegrationTest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @Qualifier(WeeklyRankingJobConfig.JOB_NAME) private val job: Job,
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
    @DisplayName("전체 Job 실행은")
    inner class FullJobExecutionTest {

        @DisplayName("Step 1에서 메트릭을 집계하고 Step 2에서 TOP 100을 RDB에 저장한다")
        @Test
        fun shouldAggregateMetricsAndSaveTop100() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            // baseDate=2025-01-08 -> 조회 범위: 2025-01-01 ~ 2025-01-07

            // 7일간의 메트릭 데이터 삽입 (상품 3개)
            // 상품 100: 총 점수 = (10+20+30)*0.1 + (1+2+3)*0.2 + (100+200+300)*0.6 = 6 + 1.2 + 360 = 367.2
            insertMetric(LocalDate.of(2025, 1, 1), 100L, 10, 1, BigDecimal("100"))
            insertMetric(LocalDate.of(2025, 1, 4), 100L, 20, 2, BigDecimal("200"))
            insertMetric(LocalDate.of(2025, 1, 7), 100L, 30, 3, BigDecimal("300"))

            // 상품 200: 총 점수 = 50*0.1 + 10*0.2 + 500*0.6 = 5 + 2 + 300 = 307
            insertMetric(LocalDate.of(2025, 1, 2), 200L, 50, 10, BigDecimal("500"))

            // 상품 300: 총 점수 = 100*0.1 + 5*0.2 + 50*0.6 = 10 + 1 + 30 = 41
            insertMetric(LocalDate.of(2025, 1, 3), 300L, 100, 5, BigDecimal("50"))

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)
            assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)

            // Step 실행 확인 (2개 Step)
            assertThat(jobExecution.stepExecutions).hasSize(2)

            // RDB에 저장된 랭킹 확인
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE base_date = ?",
                Int::class.java,
                baseDate,
            )
            assertThat(count).isEqualTo(3)

            // 순위 확인 (점수 내림차순)
            // 1등: 상품 100 (367.2)
            // 2등: 상품 200 (307)
            // 3등: 상품 300 (41)
            val rank1ProductId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM mv_product_rank_weekly WHERE base_date = ? AND `rank` = 1",
                Long::class.java,
                baseDate,
            )
            assertThat(rank1ProductId).isEqualTo(100L)

            val rank2ProductId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM mv_product_rank_weekly WHERE base_date = ? AND `rank` = 2",
                Long::class.java,
                baseDate,
            )
            assertThat(rank2ProductId).isEqualTo(200L)

            val rank3ProductId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM mv_product_rank_weekly WHERE base_date = ? AND `rank` = 3",
                Long::class.java,
                baseDate,
            )
            assertThat(rank3ProductId).isEqualTo(300L)

            // Redis 스테이징 키가 삭제되었는지 확인
            val stagingKey = "ranking:products:weekly:20250108:staging"
            val exists = redisTemplate.hasKey(stagingKey)
            assertThat(exists).isFalse()
        }

        @DisplayName("메트릭 데이터가 없어도 Job은 성공적으로 완료된다")
        @Test
        fun shouldCompleteSuccessfully_whenNoMetricData() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 9)
            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)
            assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)

            // RDB에 저장된 랭킹 없음
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly",
                Int::class.java,
            )
            assertThat(count).isEqualTo(0)
        }

        @DisplayName("100개 초과 상품이 있어도 TOP 100만 저장한다")
        @Test
        fun shouldSaveOnlyTop100_whenMoreThan100Products() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 10)

            // 110개 상품의 메트릭 데이터 삽입 (viewCount 내림차순 점수)
            (1..110).forEach { i ->
                insertMetric(
                    LocalDate.of(2025, 1, 3),
                    i.toLong(),
                    (1000 - i).toLong(),
                    10,
                    BigDecimal("100"),
                )
            }

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE base_date = ?",
                Int::class.java,
                baseDate,
            )
            assertThat(count).isEqualTo(100)

            // 상품 1이 가장 높은 점수 (viewCount 999)로 1등
            val rank1ProductId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM mv_product_rank_weekly WHERE base_date = ? AND `rank` = 1",
                Long::class.java,
                baseDate,
            )
            assertThat(rank1ProductId).isEqualTo(1L)
        }

        @DisplayName("같은 상품의 여러 날 메트릭이 합산되어 점수가 계산된다")
        @Test
        fun shouldAggregateMultipleDaysMetricsPerProduct() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 11)

            // 상품 100: 3일간의 데이터
            insertMetric(LocalDate.of(2025, 1, 4), 100L, 100, 10, BigDecimal("1000"))
            insertMetric(LocalDate.of(2025, 1, 5), 100L, 100, 10, BigDecimal("1000"))
            insertMetric(LocalDate.of(2025, 1, 6), 100L, 100, 10, BigDecimal("1000"))

            // 상품 200: 1일간의 데이터 (높은 점수)
            insertMetric(LocalDate.of(2025, 1, 4), 200L, 500, 50, BigDecimal("5000"))

            // 상품 100 총점: 3 * (100*0.1 + 10*0.2 + 1000*0.6) = 3 * (10 + 2 + 600) = 1836
            // 상품 200 총점: 1 * (500*0.1 + 50*0.2 + 5000*0.6) = 50 + 10 + 3000 = 3060

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

            // 상품 200이 1등 (3060점)
            val rank1ProductId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM mv_product_rank_weekly WHERE base_date = ? AND `rank` = 1",
                Long::class.java,
                baseDate,
            )
            assertThat(rank1ProductId).isEqualTo(200L)

            // 상품 100이 2등 (1836점)
            val rank2ProductId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM mv_product_rank_weekly WHERE base_date = ? AND `rank` = 2",
                Long::class.java,
                baseDate,
            )
            assertThat(rank2ProductId).isEqualTo(100L)
        }
    }

    @Nested
    @DisplayName("개별 Step 테스트는")
    inner class IndividualStepTest {

        @DisplayName("metricAggregationStep이 메트릭을 Redis에 집계한다")
        @Test
        fun shouldAggregateMetricsToRedis() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 12)
            val stagingKey = "ranking:products:weekly:20250112:staging"

            insertMetric(LocalDate.of(2025, 1, 5), 100L, 100, 10, BigDecimal("1000"))
            insertMetric(LocalDate.of(2025, 1, 6), 200L, 50, 5, BigDecimal("500"))

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val stepExecution = jobLauncherTestUtils.launchStep(
                "weeklyMetricAggregationStep",
                jobParameters,
            )

            // assert
            assertThat(stepExecution.status).isEqualTo(BatchStatus.COMPLETED)

            val zSetOps = redisTemplate.opsForZSet()
            val score100 = zSetOps.score(stagingKey, "100")
            val score200 = zSetOps.score(stagingKey, "200")

            // 상품 100: 100*0.1 + 10*0.2 + 1000*0.6 = 612
            assertThat(score100).isEqualTo(612.0)
            // 상품 200: 50*0.1 + 5*0.2 + 500*0.6 = 306
            assertThat(score200).isEqualTo(306.0)
        }

        @DisplayName("rankingPersistenceStep이 Redis 데이터를 RDB에 저장한다")
        @Test
        fun shouldPersistRedisDataToRdb() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 13)
            val stagingKey = "ranking:products:weekly:20250113:staging"

            // Redis에 직접 데이터 설정
            val zSetOps = redisTemplate.opsForZSet()
            zSetOps.add(stagingKey, "100", 1000.0)
            zSetOps.add(stagingKey, "200", 500.0)

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val stepExecution = jobLauncherTestUtils.launchStep(
                "weeklyRankingPersistenceStep",
                jobParameters,
            )

            // assert
            assertThat(stepExecution.status).isEqualTo(BatchStatus.COMPLETED)

            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE base_date = ?",
                Int::class.java,
                baseDate,
            )
            assertThat(count).isEqualTo(2)

            // 스테이징 키 삭제 확인
            assertThat(redisTemplate.hasKey(stagingKey)).isFalse()
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
