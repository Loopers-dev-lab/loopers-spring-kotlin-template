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
@TestPropertySource(properties = ["spring.batch.job.name=${MonthlyRankingJobConfig.JOB_NAME}"])
@DisplayName("MonthlyRankingJob Integration")
class MonthlyRankingJobIntegrationTest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @Qualifier(MonthlyRankingJobConfig.JOB_NAME) private val job: Job,
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

        @DisplayName("Step 1에서 30일 메트릭을 집계하고 Step 2에서 TOP 100을 RDB에 저장한다")
        @Test
        fun shouldAggregateMonthlyMetricsAndSaveTop100() {
            // arrange
            val baseDate = LocalDate.of(2025, 2, 1)
            // baseDate=2025-02-01 -> 조회 범위: 2025-01-02 ~ 2025-01-31 (30일)

            // 30일간의 메트릭 데이터 삽입 (상품 3개)
            // 상품 100: 7일간 데이터 (1주차)
            insertMetric(LocalDate.of(2025, 1, 2), 100L, 10, 1, BigDecimal("100"))
            insertMetric(LocalDate.of(2025, 1, 8), 100L, 20, 2, BigDecimal("200"))
            insertMetric(LocalDate.of(2025, 1, 15), 100L, 30, 3, BigDecimal("300"))
            insertMetric(LocalDate.of(2025, 1, 22), 100L, 40, 4, BigDecimal("400"))
            // 상품 100 총점: (10+20+30+40)*0.1 + (1+2+3+4)*0.2 + (100+200+300+400)*0.6
            //             = 10 + 2 + 600 = 612

            // 상품 200: 15일간 데이터 (2주)
            insertMetric(LocalDate.of(2025, 1, 5), 200L, 100, 20, BigDecimal("1000"))
            insertMetric(LocalDate.of(2025, 1, 20), 200L, 100, 20, BigDecimal("1000"))
            // 상품 200 총점: (100+100)*0.1 + (20+20)*0.2 + (1000+1000)*0.6
            //             = 20 + 8 + 1200 = 1228

            // 상품 300: 25일 데이터
            insertMetric(LocalDate.of(2025, 1, 7), 300L, 50, 5, BigDecimal("500"))
            // 상품 300 총점: 50*0.1 + 5*0.2 + 500*0.6 = 5 + 1 + 300 = 306

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

            // RDB에 저장된 월간 랭킹 확인
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE base_date = ?",
                Int::class.java,
                baseDate,
            )
            assertThat(count).isEqualTo(3)

            // 순위 확인 (점수 내림차순)
            // 1등: 상품 200 (1228)
            // 2등: 상품 100 (612)
            // 3등: 상품 300 (306)
            val rank1ProductId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM mv_product_rank_monthly WHERE base_date = ? AND `rank` = 1",
                Long::class.java,
                baseDate,
            )
            assertThat(rank1ProductId).isEqualTo(200L)

            val rank2ProductId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM mv_product_rank_monthly WHERE base_date = ? AND `rank` = 2",
                Long::class.java,
                baseDate,
            )
            assertThat(rank2ProductId).isEqualTo(100L)

            val rank3ProductId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM mv_product_rank_monthly WHERE base_date = ? AND `rank` = 3",
                Long::class.java,
                baseDate,
            )
            assertThat(rank3ProductId).isEqualTo(300L)

            // Redis 스테이징 키가 삭제되었는지 확인
            val stagingKey = "ranking:products:monthly:20250201:staging"
            val exists = redisTemplate.hasKey(stagingKey)
            assertThat(exists).isFalse()
        }

        @DisplayName("메트릭 데이터가 없어도 Job은 성공적으로 완료된다")
        @Test
        fun shouldCompleteSuccessfully_whenNoMetricData() {
            // arrange
            val baseDate = LocalDate.of(2025, 2, 2)
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
                "SELECT COUNT(*) FROM mv_product_rank_monthly",
                Int::class.java,
            )
            assertThat(count).isEqualTo(0)
        }

        @DisplayName("100개 초과 상품이 있어도 TOP 100만 저장한다")
        @Test
        fun shouldSaveOnlyTop100_whenMoreThan100Products() {
            // arrange
            val baseDate = LocalDate.of(2025, 2, 3)

            // 110개 상품의 메트릭 데이터 삽입 (30일 윈도우 내)
            (1..110).forEach { i ->
                insertMetric(
                    LocalDate.of(2025, 1, 10),
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
                "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE base_date = ?",
                Int::class.java,
                baseDate,
            )
            assertThat(count).isEqualTo(100)

            // 상품 1이 가장 높은 점수 (viewCount 999)로 1등
            val rank1ProductId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM mv_product_rank_monthly WHERE base_date = ? AND `rank` = 1",
                Long::class.java,
                baseDate,
            )
            assertThat(rank1ProductId).isEqualTo(1L)
        }

        @DisplayName("같은 상품의 30일간 메트릭이 합산되어 점수가 계산된다")
        @Test
        fun shouldAggregate30DaysMetricsPerProduct() {
            // arrange
            val baseDate = LocalDate.of(2025, 2, 4)

            // 상품 100: 30일 중 4주간의 데이터 (주 1회씩)
            insertMetric(LocalDate.of(2025, 1, 5), 100L, 100, 10, BigDecimal("1000"))
            insertMetric(LocalDate.of(2025, 1, 12), 100L, 100, 10, BigDecimal("1000"))
            insertMetric(LocalDate.of(2025, 1, 19), 100L, 100, 10, BigDecimal("1000"))
            insertMetric(LocalDate.of(2025, 1, 26), 100L, 100, 10, BigDecimal("1000"))

            // 상품 200: 1일간의 데이터 (높은 단일 점수)
            insertMetric(LocalDate.of(2025, 1, 15), 200L, 500, 50, BigDecimal("5000"))

            // 상품 100 총점: 4 * (100*0.1 + 10*0.2 + 1000*0.6) = 4 * (10 + 2 + 600) = 2448
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
                "SELECT product_id FROM mv_product_rank_monthly WHERE base_date = ? AND `rank` = 1",
                Long::class.java,
                baseDate,
            )
            assertThat(rank1ProductId).isEqualTo(200L)

            // 상품 100이 2등 (2448점)
            val rank2ProductId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM mv_product_rank_monthly WHERE base_date = ? AND `rank` = 2",
                Long::class.java,
                baseDate,
            )
            assertThat(rank2ProductId).isEqualTo(100L)
        }

        @DisplayName("30일 윈도우 밖의 데이터는 집계에서 제외된다")
        @Test
        fun shouldExcludeDataOutside30DayWindow() {
            // arrange
            val baseDate = LocalDate.of(2025, 2, 5)
            // baseDate=2025-02-05 -> 조회 범위: 2025-01-06 ~ 2025-02-04 (30일)

            // 윈도우 내 데이터 (2025-01-06 ~ 2025-02-04)
            insertMetric(LocalDate.of(2025, 1, 10), 100L, 100, 10, BigDecimal("1000"))

            // 윈도우 외 데이터 (2025-01-05 이전)
            insertMetric(LocalDate.of(2025, 1, 5), 200L, 1000, 100, BigDecimal("10000"))

            // 당일 데이터 (제외됨)
            insertMetric(LocalDate.of(2025, 2, 5), 300L, 500, 50, BigDecimal("5000"))

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            // assert
            assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

            // 상품 100만 포함됨 (윈도우 내 유일한 데이터)
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE base_date = ?",
                Int::class.java,
                baseDate,
            )
            assertThat(count).isEqualTo(1)

            val rank1ProductId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM mv_product_rank_monthly WHERE base_date = ? AND `rank` = 1",
                Long::class.java,
                baseDate,
            )
            assertThat(rank1ProductId).isEqualTo(100L)
        }
    }

    @Nested
    @DisplayName("개별 Step 테스트는")
    inner class IndividualStepTest {

        @DisplayName("monthlyMetricAggregationStep이 30일 메트릭을 Redis에 집계한다")
        @Test
        fun shouldAggregateMonthlyMetricsToRedis() {
            // arrange
            val baseDate = LocalDate.of(2025, 2, 10)
            val stagingKey = "ranking:products:monthly:20250210:staging"

            // 30일 윈도우 내 데이터 삽입
            insertMetric(LocalDate.of(2025, 1, 15), 100L, 100, 10, BigDecimal("1000"))
            insertMetric(LocalDate.of(2025, 1, 20), 200L, 50, 5, BigDecimal("500"))

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val stepExecution = jobLauncherTestUtils.launchStep(
                "monthlyMetricAggregationStep",
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

        @DisplayName("monthlyRankingPersistenceStep이 Redis 데이터를 월간 테이블에 저장한다")
        @Test
        fun shouldPersistRedisDataToMonthlyTable() {
            // arrange
            val baseDate = LocalDate.of(2025, 2, 11)
            val stagingKey = "ranking:products:monthly:20250211:staging"

            // Redis에 직접 데이터 설정
            val zSetOps = redisTemplate.opsForZSet()
            zSetOps.add(stagingKey, "100", 1000.0)
            zSetOps.add(stagingKey, "200", 500.0)

            val jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("baseDate", baseDate)
                .toJobParameters()

            // act
            val stepExecution = jobLauncherTestUtils.launchStep(
                "monthlyRankingPersistenceStep",
                jobParameters,
            )

            // assert
            assertThat(stepExecution.status).isEqualTo(BatchStatus.COMPLETED)

            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE base_date = ?",
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
