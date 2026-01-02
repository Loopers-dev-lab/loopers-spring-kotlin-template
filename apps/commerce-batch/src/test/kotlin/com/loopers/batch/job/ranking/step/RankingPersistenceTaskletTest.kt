package com.loopers.batch.job.ranking.step

import com.loopers.batch.job.ranking.RankingPeriodType
import com.loopers.batch.job.ranking.WeeklyRankingJobConfig
import com.loopers.domain.ranking.ProductPeriodRankingRepository
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.batch.test.MetaDataInstanceFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.batch.job.name=${WeeklyRankingJobConfig.JOB_NAME}"])
@DisplayName("RankingPersistenceTasklet")
class RankingPersistenceTaskletTest @Autowired constructor(
    private val redisTemplate: RedisTemplate<String, String>,
    private val productPeriodRankingRepository: ProductPeriodRankingRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @BeforeEach
    fun setUp() {
        // Redis 초기화
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
        // DB 초기화
        databaseCleanUp.afterPropertiesSet()
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("WEEKLY 기간에서 execute 메서드는")
    inner class WeeklyExecuteTest {

        @DisplayName("Redis에서 TOP 100을 추출하여 주간 테이블에 저장한다")
        @Test
        fun shouldExtractTop100AndSaveToWeeklyTable() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val stagingKey = "ranking:products:weekly:20250108:staging"
            val tasklet = createTasklet(baseDate, RankingPeriodType.WEEKLY)

            // Redis에 테스트 데이터 설정 (점수가 높은 순으로 정렬됨)
            val zSetOps = redisTemplate.opsForZSet()
            zSetOps.add(stagingKey, "100", 1000.0) // 1등
            zSetOps.add(stagingKey, "200", 900.0) // 2등
            zSetOps.add(stagingKey, "300", 800.0) // 3등
            zSetOps.add(stagingKey, "400", 700.0) // 4등
            zSetOps.add(stagingKey, "500", 600.0) // 5등

            val stepExecution = createStepExecution()
            val contribution = StepContribution(stepExecution)
            val chunkContext = ChunkContext(StepContext(stepExecution))

            // act
            val result = tasklet.execute(contribution, chunkContext)

            // assert
            assertThat(result).isEqualTo(RepeatStatus.FINISHED)

            // RDB에 저장된 데이터 확인
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE base_date = ?",
                Int::class.java,
                baseDate,
            )
            assertThat(count).isEqualTo(5)

            // 순위 확인 (rank 1이 가장 높은 점수)
            val rank1ProductId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM mv_product_rank_weekly WHERE base_date = ? AND `rank` = 1",
                Long::class.java,
                baseDate,
            )
            assertThat(rank1ProductId).isEqualTo(100L)

            val rank5ProductId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM mv_product_rank_weekly WHERE base_date = ? AND `rank` = 5",
                Long::class.java,
                baseDate,
            )
            assertThat(rank5ProductId).isEqualTo(500L)
        }

        @DisplayName("스테이징 키가 비어있으면 아무것도 저장하지 않고 완료한다")
        @Test
        fun shouldFinishWithoutSaving_whenStagingKeyIsEmpty() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val tasklet = createTasklet(baseDate, RankingPeriodType.WEEKLY)

            val stepExecution = createStepExecution()
            val contribution = StepContribution(stepExecution)
            val chunkContext = ChunkContext(StepContext(stepExecution))

            // act
            val result = tasklet.execute(contribution, chunkContext)

            // assert
            assertThat(result).isEqualTo(RepeatStatus.FINISHED)

            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly",
                Int::class.java,
            )
            assertThat(count).isEqualTo(0)
        }

        @DisplayName("기존 랭킹 데이터를 삭제하고 새로운 데이터를 저장한다")
        @Test
        fun shouldDeleteExistingAndSaveNew() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val stagingKey = "ranking:products:weekly:20250108:staging"
            val tasklet = createTasklet(baseDate, RankingPeriodType.WEEKLY)

            // 기존 데이터 삽입
            jdbcTemplate.update(
                "INSERT INTO mv_product_rank_weekly (base_date, `rank`, product_id, score, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                baseDate,
                1,
                999L,
                BigDecimal("999.00"),
            )

            // Redis에 새 데이터 설정
            val zSetOps = redisTemplate.opsForZSet()
            zSetOps.add(stagingKey, "100", 1000.0)
            zSetOps.add(stagingKey, "200", 900.0)

            val stepExecution = createStepExecution()
            val contribution = StepContribution(stepExecution)
            val chunkContext = ChunkContext(StepContext(stepExecution))

            // act
            val result = tasklet.execute(contribution, chunkContext)

            // assert
            assertThat(result).isEqualTo(RepeatStatus.FINISHED)

            // 기존 데이터(product_id=999)는 삭제되고 새 데이터만 존재
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE base_date = ?",
                Int::class.java,
                baseDate,
            )
            assertThat(count).isEqualTo(2)

            val oldDataCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE product_id = 999",
                Int::class.java,
            )
            assertThat(oldDataCount).isEqualTo(0)
        }

        @DisplayName("저장 후 스테이징 키를 삭제한다")
        @Test
        fun shouldDeleteStagingKeyAfterSave() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val stagingKey = "ranking:products:weekly:20250108:staging"
            val tasklet = createTasklet(baseDate, RankingPeriodType.WEEKLY)

            val zSetOps = redisTemplate.opsForZSet()
            zSetOps.add(stagingKey, "100", 1000.0)

            val stepExecution = createStepExecution()
            val contribution = StepContribution(stepExecution)
            val chunkContext = ChunkContext(StepContext(stepExecution))

            // act
            tasklet.execute(contribution, chunkContext)

            // assert
            val exists = redisTemplate.hasKey(stagingKey)
            assertThat(exists).isFalse()
        }

        @DisplayName("100개 초과 데이터가 있어도 TOP 100만 저장한다")
        @Test
        fun shouldSaveOnlyTop100_whenMoreThan100Exists() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val stagingKey = "ranking:products:weekly:20250108:staging"
            val tasklet = createTasklet(baseDate, RankingPeriodType.WEEKLY)

            // Redis에 110개 데이터 설정
            val zSetOps = redisTemplate.opsForZSet()
            (1..110).forEach { i ->
                zSetOps.add(stagingKey, i.toString(), (10000 - i).toDouble())
            }

            val stepExecution = createStepExecution()
            val contribution = StepContribution(stepExecution)
            val chunkContext = ChunkContext(StepContext(stepExecution))

            // act
            tasklet.execute(contribution, chunkContext)

            // assert
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE base_date = ?",
                Int::class.java,
                baseDate,
            )
            assertThat(count).isEqualTo(100)

            // 가장 높은 점수(9999)를 가진 product_id=1이 rank 1
            val rank1ProductId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM mv_product_rank_weekly WHERE base_date = ? AND `rank` = 1",
                Long::class.java,
                baseDate,
            )
            assertThat(rank1ProductId).isEqualTo(1L)

            // product_id=101~110은 저장되지 않음 (TOP 100 밖)
            val excludedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE product_id > 100",
                Int::class.java,
            )
            assertThat(excludedCount).isEqualTo(0)
        }

        @DisplayName("점수가 소수점인 경우에도 정확하게 저장한다")
        @Test
        fun shouldSaveDecimalScoresAccurately() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val stagingKey = "ranking:products:weekly:20250108:staging"
            val tasklet = createTasklet(baseDate, RankingPeriodType.WEEKLY)

            val zSetOps = redisTemplate.opsForZSet()
            zSetOps.add(stagingKey, "100", 1234.56)
            zSetOps.add(stagingKey, "200", 789.12)

            val stepExecution = createStepExecution()
            val contribution = StepContribution(stepExecution)
            val chunkContext = ChunkContext(StepContext(stepExecution))

            // act
            tasklet.execute(contribution, chunkContext)

            // assert
            val score = jdbcTemplate.queryForObject(
                "SELECT score FROM mv_product_rank_weekly WHERE base_date = ? AND `rank` = 1",
                BigDecimal::class.java,
                baseDate,
            )
            assertThat(score).isEqualByComparingTo(BigDecimal("1234.56"))
        }

        @DisplayName("StepContribution에 쓰기 수를 기록한다")
        @Test
        fun shouldRecordWriteCountInContribution() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val stagingKey = "ranking:products:weekly:20250108:staging"
            val tasklet = createTasklet(baseDate, RankingPeriodType.WEEKLY)

            val zSetOps = redisTemplate.opsForZSet()
            zSetOps.add(stagingKey, "100", 1000.0)
            zSetOps.add(stagingKey, "200", 900.0)
            zSetOps.add(stagingKey, "300", 800.0)

            val stepExecution = createStepExecution()
            val contribution = StepContribution(stepExecution)
            val chunkContext = ChunkContext(StepContext(stepExecution))

            // act
            tasklet.execute(contribution, chunkContext)

            // assert
            assertThat(contribution.writeCount).isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("MONTHLY 기간에서 execute 메서드는")
    inner class MonthlyExecuteTest {

        @DisplayName("Redis에서 TOP 100을 추출하여 월간 테이블에 저장한다")
        @Test
        fun shouldExtractTop100AndSaveToMonthlyTable() {
            // arrange
            val baseDate = LocalDate.of(2025, 2, 1)
            val stagingKey = "ranking:products:monthly:20250201:staging"
            val tasklet = createTasklet(baseDate, RankingPeriodType.MONTHLY)

            // Redis에 테스트 데이터 설정
            val zSetOps = redisTemplate.opsForZSet()
            zSetOps.add(stagingKey, "100", 2000.0) // 1등
            zSetOps.add(stagingKey, "200", 1500.0) // 2등
            zSetOps.add(stagingKey, "300", 1000.0) // 3등

            val stepExecution = createStepExecution()
            val contribution = StepContribution(stepExecution)
            val chunkContext = ChunkContext(StepContext(stepExecution))

            // act
            val result = tasklet.execute(contribution, chunkContext)

            // assert
            assertThat(result).isEqualTo(RepeatStatus.FINISHED)

            // 월간 테이블에 저장 확인
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE base_date = ?",
                Int::class.java,
                baseDate,
            )
            assertThat(count).isEqualTo(3)

            // 순위 확인
            val rank1ProductId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM mv_product_rank_monthly WHERE base_date = ? AND `rank` = 1",
                Long::class.java,
                baseDate,
            )
            assertThat(rank1ProductId).isEqualTo(100L)

            // 주간 테이블에는 저장되지 않음
            val weeklyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly",
                Int::class.java,
            )
            assertThat(weeklyCount).isEqualTo(0)
        }

        @DisplayName("기존 월간 랭킹 데이터를 삭제하고 새로운 데이터를 저장한다")
        @Test
        fun shouldDeleteExistingMonthlyAndSaveNew() {
            // arrange
            val baseDate = LocalDate.of(2025, 2, 1)
            val stagingKey = "ranking:products:monthly:20250201:staging"
            val tasklet = createTasklet(baseDate, RankingPeriodType.MONTHLY)

            // 기존 월간 데이터 삽입
            jdbcTemplate.update(
                "INSERT INTO mv_product_rank_monthly (base_date, `rank`, product_id, score, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                baseDate,
                1,
                888L,
                BigDecimal("888.00"),
            )

            // Redis에 새 데이터 설정
            val zSetOps = redisTemplate.opsForZSet()
            zSetOps.add(stagingKey, "100", 2000.0)

            val stepExecution = createStepExecution()
            val contribution = StepContribution(stepExecution)
            val chunkContext = ChunkContext(StepContext(stepExecution))

            // act
            val result = tasklet.execute(contribution, chunkContext)

            // assert
            assertThat(result).isEqualTo(RepeatStatus.FINISHED)

            // 기존 데이터는 삭제되고 새 데이터만 존재
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE base_date = ?",
                Int::class.java,
                baseDate,
            )
            assertThat(count).isEqualTo(1)

            val oldDataCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE product_id = 888",
                Int::class.java,
            )
            assertThat(oldDataCount).isEqualTo(0)
        }

        @DisplayName("월간 스테이징 키를 삭제한다")
        @Test
        fun shouldDeleteMonthlyStagingKeyAfterSave() {
            // arrange
            val baseDate = LocalDate.of(2025, 2, 1)
            val stagingKey = "ranking:products:monthly:20250201:staging"
            val tasklet = createTasklet(baseDate, RankingPeriodType.MONTHLY)

            val zSetOps = redisTemplate.opsForZSet()
            zSetOps.add(stagingKey, "100", 2000.0)

            val stepExecution = createStepExecution()
            val contribution = StepContribution(stepExecution)
            val chunkContext = ChunkContext(StepContext(stepExecution))

            // act
            tasklet.execute(contribution, chunkContext)

            // assert
            val exists = redisTemplate.hasKey(stagingKey)
            assertThat(exists).isFalse()
        }
    }

    private fun createTasklet(baseDate: LocalDate, periodType: RankingPeriodType): RankingPersistenceTasklet {
        return RankingPersistenceTasklet(
            redisTemplate,
            productPeriodRankingRepository,
            baseDate,
            periodType,
        )
    }

    private fun createStepExecution(): StepExecution {
        return MetaDataInstanceFactory.createStepExecution()
    }
}
