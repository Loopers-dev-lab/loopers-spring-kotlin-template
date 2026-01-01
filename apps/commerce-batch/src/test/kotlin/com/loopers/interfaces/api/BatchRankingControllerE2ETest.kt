package com.loopers.interfaces.api

import com.loopers.batch.job.ranking.MonthlyRankingJobConfig
import com.loopers.batch.job.ranking.RankingPeriodType
import com.loopers.batch.job.ranking.WeeklyRankingJobConfig
import com.loopers.batch.job.ranking.step.MetricAggregationReader
import com.loopers.batch.job.ranking.step.RankingPersistenceTasklet
import com.loopers.batch.job.ranking.step.RedisAggregationWriter
import com.loopers.batch.job.ranking.step.ScoreCalculationProcessor
import com.loopers.batch.job.ranking.step.ScoreEntry
import com.loopers.batch.listener.ChunkListener
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import com.loopers.domain.ranking.ProductDailyMetric
import com.loopers.domain.ranking.ProductPeriodRankingRepository
import com.loopers.interfaces.api.dto.BatchExecutionResponse
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal
import java.sql.Date
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.sql.DataSource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(
    MySqlTestContainersConfig::class,
    RedisTestContainersConfig::class,
    BatchRankingControllerE2ETest.TestJobConfig::class,
)
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.main.web-application-type=servlet",
        "spring.batch.job.enabled=false",
    ],
)
@DisplayName("BatchRankingController E2E Test")
class BatchRankingControllerE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val jdbcTemplate: JdbcTemplate,
    private val redisTemplate: RedisTemplate<String, String>,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
    }

    @BeforeEach
    fun setUp() {
        databaseCleanUp.afterPropertiesSet()
        databaseCleanUp.truncateAllTables()
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
    }

    @Nested
    @DisplayName("POST /api/v1/admin/batch/rankings/{period}")
    inner class ExecuteRankingBatch {

        @DisplayName("weekly 배치를 수동 실행하면 성공한다")
        @Test
        fun shouldExecuteWeeklyBatch_whenCalled() {
            // arrange
            val today = LocalDate.now()
            val baseDateString = today.format(DATE_FORMATTER)

            // 테스트용 메트릭 데이터 삽입 (오늘 기준 7일 이내)
            insertMetric(today.minusDays(2), 100L, 100, 10, BigDecimal("1000"))
            insertMetric(today.minusDays(3), 200L, 50, 5, BigDecimal("500"))

            // act
            val response = executeBatch("weekly", baseDateString)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data?.jobName).isEqualTo(WeeklyRankingJobConfig.JOB_NAME) },
                { assertThat(response.body?.data?.baseDate).isEqualTo(baseDateString) },
                { assertThat(response.body?.data?.status).isEqualTo("COMPLETED") },
            )

            // RDB에 랭킹이 저장되었는지 확인 (오늘 날짜 기준)
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE base_date = ?",
                Int::class.java,
                today,
            )
            assertThat(count).isEqualTo(2)
        }

        @DisplayName("monthly 배치를 수동 실행하면 성공한다")
        @Test
        fun shouldExecuteMonthlyBatch_whenCalled() {
            // arrange
            val today = LocalDate.now()
            val baseDateString = today.format(DATE_FORMATTER)

            // 테스트용 메트릭 데이터 삽입 (오늘 기준 30일 이내)
            insertMetric(today.minusDays(10), 100L, 100, 10, BigDecimal("1000"))
            insertMetric(today.minusDays(20), 200L, 50, 5, BigDecimal("500"))

            // act
            val response = executeBatch("monthly", baseDateString)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data?.jobName).isEqualTo(MonthlyRankingJobConfig.JOB_NAME) },
                { assertThat(response.body?.data?.baseDate).isEqualTo(baseDateString) },
                { assertThat(response.body?.data?.status).isEqualTo("COMPLETED") },
            )

            // RDB에 랭킹이 저장되었는지 확인 (오늘 날짜 기준)
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE base_date = ?",
                Int::class.java,
                today,
            )
            assertThat(count).isEqualTo(2)
        }

        @DisplayName("baseDate가 없으면 오늘 날짜로 실행한다")
        @Test
        fun shouldUseToday_whenBaseDateIsNull() {
            // act
            val response = executeBatch("weekly", null)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.baseDate).isEqualTo(LocalDate.now().format(DATE_FORMATTER)) },
            )
        }

        @DisplayName("미래 날짜는 오늘 날짜로 대체된다")
        @Test
        fun shouldReplaceWithToday_whenFutureDateProvided() {
            // arrange
            val futureDate = LocalDate.now().plusDays(10)
            val futureDateString = futureDate.format(DATE_FORMATTER)

            // act
            val response = executeBatch("weekly", futureDateString)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.baseDate).isEqualTo(LocalDate.now().format(DATE_FORMATTER)) },
            )
        }

        @DisplayName("잘못된 period 값이면 400 에러를 반환한다")
        @Test
        fun shouldReturn400_whenInvalidPeriod() {
            // act
            val response = executeBatch("invalid_period", "20250108")

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("INVALID_PERIOD") },
            )
        }

        @DisplayName("잘못된 날짜 형식이면 400 에러를 반환한다")
        @Test
        fun shouldReturn400_whenInvalidDateFormat() {
            // act
            val response = executeBatch("weekly", "2025-01-08") // Wrong format

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("INVALID_DATE_FORMAT") },
            )
        }

        @DisplayName("데이터가 없어도 배치는 성공적으로 완료된다")
        @Test
        fun shouldCompleteSuccessfully_whenNoData() {
            // act
            val response = executeBatch("weekly", LocalDate.now().format(DATE_FORMATTER))

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.status).isEqualTo("COMPLETED") },
                { assertThat(response.body?.data?.readCount).isEqualTo(0L) },
            )
        }

        @DisplayName("동일 날짜로 재실행하면 데이터를 덮어쓴다")
        @Test
        fun shouldOverwriteData_whenReExecutedForSameDate() {
            // arrange
            val today = LocalDate.now()
            val baseDateString = today.format(DATE_FORMATTER)

            // 첫 번째 실행 (1개의 메트릭)
            insertMetric(today.minusDays(2), 100L, 100, 10, BigDecimal("1000"))
            executeBatch("weekly", baseDateString)

            // 데이터 추가 (2번째 메트릭)
            insertMetric(today.minusDays(3), 200L, 200, 20, BigDecimal("2000"))

            // act - 두 번째 실행
            val response = executeBatch("weekly", baseDateString)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.status).isEqualTo("COMPLETED") },
            )

            // 덮어쓰기가 되어 2개의 랭킹이 존재해야 함
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE base_date = ?",
                Int::class.java,
                today,
            )
            assertThat(count).isEqualTo(2)
        }
    }

    private fun executeBatch(
        period: String,
        baseDate: String?,
    ): ResponseEntity<ApiResponse<BatchExecutionResponse>> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        val body = if (baseDate != null) {
            mapOf("baseDate" to baseDate)
        } else {
            emptyMap()
        }

        return testRestTemplate.exchange(
            "/api/v1/admin/batch/rankings/$period",
            HttpMethod.POST,
            HttpEntity(body, headers),
            object : ParameterizedTypeReference<ApiResponse<BatchExecutionResponse>>() {},
        )
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

    /**
     * Test configuration that provides both weekly and monthly ranking jobs
     * without the @ConditionalOnProperty restriction.
     * These jobs are created using @Primary to override any existing beans.
     */
    @TestConfiguration
    class TestJobConfig {
        companion object {
            private const val CHUNK_SIZE = 1000
        }

        @Bean(WeeklyRankingJobConfig.JOB_NAME)
        @Primary
        fun weeklyRankingJob(
            jobRepository: JobRepository,
            transactionManager: PlatformTransactionManager,
            jobListener: JobListener,
            stepMonitorListener: StepMonitorListener,
            chunkListener: ChunkListener,
            dataSource: DataSource,
            redisTemplate: RedisTemplate<String, String>,
            productPeriodRankingRepository: ProductPeriodRankingRepository,
        ): Job {
            return JobBuilder(WeeklyRankingJobConfig.JOB_NAME, jobRepository)
                .incrementer(RunIdIncrementer())
                .start(
                    createMetricAggregationStep(
                        "weeklyMetricAggregationStep",
                        RankingPeriodType.WEEKLY,
                        jobRepository,
                        transactionManager,
                        stepMonitorListener,
                        chunkListener,
                        dataSource,
                        redisTemplate,
                    ),
                )
                .next(
                    createRankingPersistenceStep(
                        "weeklyRankingPersistenceStep",
                        RankingPeriodType.WEEKLY,
                        jobRepository,
                        transactionManager,
                        stepMonitorListener,
                        redisTemplate,
                        productPeriodRankingRepository,
                    ),
                )
                .listener(jobListener)
                .build()
        }

        @Bean(MonthlyRankingJobConfig.JOB_NAME)
        @Primary
        fun monthlyRankingJob(
            jobRepository: JobRepository,
            transactionManager: PlatformTransactionManager,
            jobListener: JobListener,
            stepMonitorListener: StepMonitorListener,
            chunkListener: ChunkListener,
            dataSource: DataSource,
            redisTemplate: RedisTemplate<String, String>,
            productPeriodRankingRepository: ProductPeriodRankingRepository,
        ): Job {
            return JobBuilder(MonthlyRankingJobConfig.JOB_NAME, jobRepository)
                .incrementer(RunIdIncrementer())
                .start(
                    createMetricAggregationStep(
                        "monthlyMetricAggregationStep",
                        RankingPeriodType.MONTHLY,
                        jobRepository,
                        transactionManager,
                        stepMonitorListener,
                        chunkListener,
                        dataSource,
                        redisTemplate,
                    ),
                )
                .next(
                    createRankingPersistenceStep(
                        "monthlyRankingPersistenceStep",
                        RankingPeriodType.MONTHLY,
                        jobRepository,
                        transactionManager,
                        stepMonitorListener,
                        redisTemplate,
                        productPeriodRankingRepository,
                    ),
                )
                .listener(jobListener)
                .build()
        }

        private fun createMetricAggregationStep(
            stepName: String,
            periodType: RankingPeriodType,
            jobRepository: JobRepository,
            transactionManager: PlatformTransactionManager,
            stepMonitorListener: StepMonitorListener,
            chunkListener: ChunkListener,
            dataSource: DataSource,
            redisTemplate: RedisTemplate<String, String>,
        ): Step {
            return StepBuilder(stepName, jobRepository)
                .chunk<ProductDailyMetric, ScoreEntry>(CHUNK_SIZE, transactionManager)
                .reader(
                    MetricAggregationReader(
                        dataSource = dataSource,
                        baseDate = LocalDate.now(),
                        windowDays = periodType.windowDays,
                    ),
                )
                .processor(ScoreCalculationProcessor())
                .writer(
                    RedisAggregationWriter(
                        redisTemplate = redisTemplate,
                        baseDate = LocalDate.now(),
                        periodType = periodType,
                    ),
                )
                .listener(stepMonitorListener)
                .listener(chunkListener)
                .build()
        }

        private fun createRankingPersistenceStep(
            stepName: String,
            periodType: RankingPeriodType,
            jobRepository: JobRepository,
            transactionManager: PlatformTransactionManager,
            stepMonitorListener: StepMonitorListener,
            redisTemplate: RedisTemplate<String, String>,
            productPeriodRankingRepository: ProductPeriodRankingRepository,
        ): Step {
            return StepBuilder(stepName, jobRepository)
                .tasklet(
                    RankingPersistenceTasklet(
                        redisTemplate = redisTemplate,
                        productPeriodRankingRepository = productPeriodRankingRepository,
                        baseDate = LocalDate.now(),
                        periodType = periodType,
                    ),
                    transactionManager,
                )
                .listener(stepMonitorListener)
                .build()
        }
    }
}
