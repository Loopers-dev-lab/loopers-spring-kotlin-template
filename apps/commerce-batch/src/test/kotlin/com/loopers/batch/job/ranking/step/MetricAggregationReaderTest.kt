package com.loopers.batch.job.ranking.step

import com.loopers.batch.job.ranking.RankingPeriodType
import com.loopers.batch.job.ranking.WeeklyRankingJobConfig
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
@TestPropertySource(properties = ["spring.batch.job.name=${WeeklyRankingJobConfig.JOB_NAME}"])
@DisplayName("MetricAggregationReader")
class MetricAggregationReaderTest @Autowired constructor(
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
    @DisplayName("WEEKLY 윈도우 조회는")
    inner class WeeklyWindowTest {

        @DisplayName("baseDate 기준 최근 7일 데이터를 조회한다 (baseDate 당일 제외)")
        @Test
        fun shouldReadLast7Days() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            // baseDate=2025-01-08 -> 조회 범위: 2025-01-01 ~ 2025-01-07

            // 범위 내 데이터 (2025-01-01 ~ 2025-01-07)
            insertMetric(LocalDate.of(2025, 1, 1), 100L, 10, 1, BigDecimal("100"))
            insertMetric(LocalDate.of(2025, 1, 4), 100L, 20, 2, BigDecimal("200"))
            insertMetric(LocalDate.of(2025, 1, 7), 100L, 30, 3, BigDecimal("300"))

            // 범위 밖 데이터 (baseDate 당일)
            insertMetric(LocalDate.of(2025, 1, 8), 100L, 40, 4, BigDecimal("400"))

            // 범위 밖 데이터 (baseDate - 8일)
            insertMetric(LocalDate.of(2024, 12, 31), 100L, 50, 5, BigDecimal("500"))

            val reader = createReader(baseDate, RankingPeriodType.WEEKLY)
            reader.afterPropertiesSet()
            reader.open(MetaDataInstanceFactory.createStepExecution().executionContext)

            // act
            val items = mutableListOf<com.loopers.domain.ranking.ProductDailyMetric>()
            var item = reader.read()
            while (item != null) {
                items.add(item)
                item = reader.read()
            }
            reader.close()

            // assert
            assertThat(items).hasSize(3)
            assertThat(items.map { it.statDate }).containsExactlyInAnyOrder(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 4),
                LocalDate.of(2025, 1, 7),
            )
        }

        @DisplayName("stat_date, product_id 순으로 정렬하여 조회한다")
        @Test
        fun shouldSortByStatDateAndProductId() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)

            insertMetric(LocalDate.of(2025, 1, 1), 200L, 10, 1, BigDecimal("100"))
            insertMetric(LocalDate.of(2025, 1, 1), 100L, 20, 2, BigDecimal("200"))
            insertMetric(LocalDate.of(2025, 1, 2), 100L, 30, 3, BigDecimal("300"))

            val reader = createReader(baseDate, RankingPeriodType.WEEKLY)
            reader.afterPropertiesSet()
            reader.open(MetaDataInstanceFactory.createStepExecution().executionContext)

            // act
            val items = mutableListOf<com.loopers.domain.ranking.ProductDailyMetric>()
            var item = reader.read()
            while (item != null) {
                items.add(item)
                item = reader.read()
            }
            reader.close()

            // assert
            assertThat(items).hasSize(3)
            // 정렬: stat_date ASC, product_id ASC
            assertThat(items[0].statDate).isEqualTo(LocalDate.of(2025, 1, 1))
            assertThat(items[0].productId).isEqualTo(100L)
            assertThat(items[1].statDate).isEqualTo(LocalDate.of(2025, 1, 1))
            assertThat(items[1].productId).isEqualTo(200L)
            assertThat(items[2].statDate).isEqualTo(LocalDate.of(2025, 1, 2))
            assertThat(items[2].productId).isEqualTo(100L)
        }

        @DisplayName("데이터가 없는 경우 빈 결과를 반환한다")
        @Test
        fun shouldReturnEmpty_whenNoData() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)

            val reader = createReader(baseDate, RankingPeriodType.WEEKLY)
            reader.afterPropertiesSet()
            reader.open(MetaDataInstanceFactory.createStepExecution().executionContext)

            // act
            val item = reader.read()

            // assert
            assertThat(item).isNull()
            reader.close()
        }
    }

    @Nested
    @DisplayName("MONTHLY 윈도우 조회는")
    inner class MonthlyWindowTest {

        @DisplayName("baseDate 기준 최근 30일 데이터를 조회한다 (baseDate 당일 제외)")
        @Test
        fun shouldReadLast30Days() {
            // arrange
            val baseDate = LocalDate.of(2025, 2, 1)
            // baseDate=2025-02-01 -> 조회 범위: 2025-01-02 ~ 2025-01-31

            // 범위 내 데이터
            insertMetric(LocalDate.of(2025, 1, 2), 100L, 10, 1, BigDecimal("100"))
            insertMetric(LocalDate.of(2025, 1, 15), 100L, 20, 2, BigDecimal("200"))
            insertMetric(LocalDate.of(2025, 1, 31), 100L, 30, 3, BigDecimal("300"))

            // 범위 밖 데이터 (baseDate 당일)
            insertMetric(LocalDate.of(2025, 2, 1), 100L, 40, 4, BigDecimal("400"))

            // 범위 밖 데이터 (baseDate - 31일)
            insertMetric(LocalDate.of(2025, 1, 1), 100L, 50, 5, BigDecimal("500"))

            val reader = createReader(baseDate, RankingPeriodType.MONTHLY)
            reader.afterPropertiesSet()
            reader.open(MetaDataInstanceFactory.createStepExecution().executionContext)

            // act
            val items = mutableListOf<com.loopers.domain.ranking.ProductDailyMetric>()
            var item = reader.read()
            while (item != null) {
                items.add(item)
                item = reader.read()
            }
            reader.close()

            // assert
            assertThat(items).hasSize(3)
            assertThat(items.map { it.statDate }).containsExactlyInAnyOrder(
                LocalDate.of(2025, 1, 2),
                LocalDate.of(2025, 1, 15),
                LocalDate.of(2025, 1, 31),
            )
        }
    }

    private fun createReader(baseDate: LocalDate, periodType: RankingPeriodType): MetricAggregationReader {
        return MetricAggregationReader(dataSource, baseDate, periodType.windowDays)
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
