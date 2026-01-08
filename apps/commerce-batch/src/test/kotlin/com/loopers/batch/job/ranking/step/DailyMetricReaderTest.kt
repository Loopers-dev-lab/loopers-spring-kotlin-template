package com.loopers.batch.job.ranking.step

import com.loopers.batch.job.ranking.DailyRankingJobConfig
import com.loopers.domain.ranking.ProductDailyMetric
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.batch.test.MetaDataInstanceFactory
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.sql.Date
import java.time.LocalDate
import javax.sql.DataSource

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.batch.job.name=${DailyRankingJobConfig.JOB_NAME}"])
@DisplayName("DailyMetricReader")
class DailyMetricReaderTest @Autowired constructor(
    private val dataSource: DataSource,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @BeforeEach
    fun setUp() {
        databaseCleanUp.afterPropertiesSet()
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("일별 메트릭 조회는")
    inner class DailyWindowTest {

        @DisplayName("baseDate 기준 오늘과 어제 데이터를 조회한다")
        @Test
        fun shouldReadTodayAndYesterdayData() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val today = baseDate
            val yesterday = baseDate.minusDays(1)

            // 범위 내 데이터 (오늘)
            insertMetric(today, 100L, 10, 1, BigDecimal("100"))
            insertMetric(today, 200L, 20, 2, BigDecimal("200"))

            // 범위 내 데이터 (어제)
            insertMetric(yesterday, 100L, 30, 3, BigDecimal("300"))

            // 범위 밖 데이터 (2일 전)
            val twoDaysAgo = baseDate.minusDays(2)
            insertMetric(twoDaysAgo, 100L, 40, 4, BigDecimal("400"))

            // 범위 밖 데이터 (내일)
            val tomorrow = baseDate.plusDays(1)
            insertMetric(tomorrow, 100L, 50, 5, BigDecimal("500"))

            val reader = DailyMetricReader(dataSource, baseDate)
            reader.open(MetaDataInstanceFactory.createStepExecution().executionContext)

            // act
            val items = mutableListOf<ProductDailyMetric>()
            var item = reader.read()
            while (item != null) {
                items.add(item)
                item = reader.read()
            }
            reader.close()

            // assert
            assertThat(items).hasSize(3)
            assertThat(items.map { it.statDate }).containsExactlyInAnyOrder(
                today,
                today,
                yesterday,
            )
        }

        @DisplayName("stat_date, product_id 순으로 정렬하여 조회한다")
        @Test
        fun shouldSortByStatDateAndProductId() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val today = baseDate
            val yesterday = baseDate.minusDays(1)

            // 순서 무작위로 삽입
            insertMetric(today, 200L, 10, 1, BigDecimal("100"))
            insertMetric(yesterday, 100L, 20, 2, BigDecimal("200"))
            insertMetric(today, 100L, 30, 3, BigDecimal("300"))

            val reader = DailyMetricReader(dataSource, baseDate)
            reader.open(MetaDataInstanceFactory.createStepExecution().executionContext)

            // act
            val items = mutableListOf<ProductDailyMetric>()
            var item = reader.read()
            while (item != null) {
                items.add(item)
                item = reader.read()
            }
            reader.close()

            // assert
            assertThat(items).hasSize(3)
            // 정렬: stat_date ASC, product_id ASC
            // 1. yesterday, 100
            // 2. today, 100
            // 3. today, 200
            assertThat(items[0].statDate).isEqualTo(yesterday)
            assertThat(items[0].productId).isEqualTo(100L)
            assertThat(items[1].statDate).isEqualTo(today)
            assertThat(items[1].productId).isEqualTo(100L)
            assertThat(items[2].statDate).isEqualTo(today)
            assertThat(items[2].productId).isEqualTo(200L)
        }

        @DisplayName("데이터가 없는 경우 빈 결과를 반환한다")
        @Test
        fun shouldReturnEmpty_whenNoData() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)

            val reader = DailyMetricReader(dataSource, baseDate)
            reader.open(MetaDataInstanceFactory.createStepExecution().executionContext)

            // act
            val item = reader.read()

            // assert
            assertThat(item).isNull()
            reader.close()
        }

        @DisplayName("오늘 데이터만 있는 경우 오늘 데이터만 조회한다")
        @Test
        fun shouldReadOnlyToday_whenYesterdayDataNotExists() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val today = baseDate

            insertMetric(today, 100L, 10, 1, BigDecimal("100"))
            insertMetric(today, 200L, 20, 2, BigDecimal("200"))

            val reader = DailyMetricReader(dataSource, baseDate)
            reader.open(MetaDataInstanceFactory.createStepExecution().executionContext)

            // act
            val items = mutableListOf<ProductDailyMetric>()
            var item = reader.read()
            while (item != null) {
                items.add(item)
                item = reader.read()
            }
            reader.close()

            // assert
            assertThat(items).hasSize(2)
            assertThat(items.map { it.statDate }).allMatch { it == today }
        }

        @DisplayName("어제 데이터만 있는 경우 어제 데이터만 조회한다")
        @Test
        fun shouldReadOnlyYesterday_whenTodayDataNotExists() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val yesterday = baseDate.minusDays(1)

            insertMetric(yesterday, 100L, 10, 1, BigDecimal("100"))

            val reader = DailyMetricReader(dataSource, baseDate)
            reader.open(MetaDataInstanceFactory.createStepExecution().executionContext)

            // act
            val items = mutableListOf<ProductDailyMetric>()
            var item = reader.read()
            while (item != null) {
                items.add(item)
                item = reader.read()
            }
            reader.close()

            // assert
            assertThat(items).hasSize(1)
            assertThat(items[0].statDate).isEqualTo(yesterday)
            assertThat(items[0].productId).isEqualTo(100L)
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
