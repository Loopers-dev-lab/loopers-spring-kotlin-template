package com.loopers.batch.job.ranking.step

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig
import com.loopers.domain.ranking.ProductDailyMetric
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.batch.test.MetaDataInstanceFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.sql.DataSource

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.batch.job.name=${WeeklyRankingJobConfig.JOB_NAME}"])
@DisplayName("AggregatedHourlyMetricReader")
class AggregatedHourlyMetricReaderTest @Autowired constructor(
    private val dataSource: DataSource,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    companion object {
        private val ZONE_ID = ZoneId.of("Asia/Seoul")
    }

    @BeforeEach
    fun setUp() {
        databaseCleanUp.afterPropertiesSet()
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("시간별 메트릭 집계 조회는")
    inner class AggregationTest {

        @DisplayName("targetDate의 시간별 메트릭을 product_id별로 집계하여 조회한다")
        @Test
        fun shouldAggregateHourlyMetricsByProductId() {
            // arrange
            val targetDate = LocalDate.of(2025, 1, 8)
            val hour10 = toInstant(targetDate, 10)
            val hour14 = toInstant(targetDate, 14)
            val hour18 = toInstant(targetDate, 18)

            // product_id=100: 3개 시간대 데이터
            insertHourlyMetric(hour10, 100L, 10, 1, BigDecimal("100"))
            insertHourlyMetric(hour14, 100L, 20, 2, BigDecimal("200"))
            insertHourlyMetric(hour18, 100L, 30, 3, BigDecimal("300"))

            // product_id=200: 1개 시간대 데이터
            insertHourlyMetric(hour14, 200L, 50, 5, BigDecimal("500"))

            val reader = AggregatedHourlyMetricReader(dataSource, targetDate)
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

            val product100 = items.find { it.productId == 100L }!!
            assertThat(product100.statDate).isEqualTo(targetDate)
            assertThat(product100.viewCount).isEqualTo(60) // 10 + 20 + 30
            assertThat(product100.likeCount).isEqualTo(6) // 1 + 2 + 3
            assertThat(product100.orderAmount).isEqualByComparingTo(BigDecimal("600")) // 100 + 200 + 300

            val product200 = items.find { it.productId == 200L }!!
            assertThat(product200.statDate).isEqualTo(targetDate)
            assertThat(product200.viewCount).isEqualTo(50)
            assertThat(product200.likeCount).isEqualTo(5)
            assertThat(product200.orderAmount).isEqualByComparingTo(BigDecimal("500"))
        }

        @DisplayName("다른 날짜의 데이터는 집계에 포함되지 않는다")
        @Test
        fun shouldNotIncludeOtherDates() {
            // arrange
            val targetDate = LocalDate.of(2025, 1, 8)
            val yesterday = targetDate.minusDays(1)
            val tomorrow = targetDate.plusDays(1)

            // targetDate 데이터
            insertHourlyMetric(toInstant(targetDate, 10), 100L, 10, 1, BigDecimal("100"))

            // 어제 데이터 (포함되지 않아야 함)
            insertHourlyMetric(toInstant(yesterday, 23), 100L, 50, 5, BigDecimal("500"))

            // 내일 데이터 (포함되지 않아야 함)
            insertHourlyMetric(toInstant(tomorrow, 0), 100L, 60, 6, BigDecimal("600"))

            val reader = AggregatedHourlyMetricReader(dataSource, targetDate)
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
            assertThat(items[0].productId).isEqualTo(100L)
            assertThat(items[0].viewCount).isEqualTo(10)
            assertThat(items[0].likeCount).isEqualTo(1)
            assertThat(items[0].orderAmount).isEqualByComparingTo(BigDecimal("100"))
        }

        @DisplayName("product_id 오름차순으로 정렬하여 조회한다")
        @Test
        fun shouldSortByProductIdAscending() {
            // arrange
            val targetDate = LocalDate.of(2025, 1, 8)
            val hour10 = toInstant(targetDate, 10)

            // 역순으로 삽입
            insertHourlyMetric(hour10, 300L, 30, 3, BigDecimal("300"))
            insertHourlyMetric(hour10, 100L, 10, 1, BigDecimal("100"))
            insertHourlyMetric(hour10, 200L, 20, 2, BigDecimal("200"))

            val reader = AggregatedHourlyMetricReader(dataSource, targetDate)
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
            assertThat(items.map { it.productId }).containsExactly(100L, 200L, 300L)
        }

        @DisplayName("데이터가 없는 경우 빈 결과를 반환한다")
        @Test
        fun shouldReturnEmpty_whenNoData() {
            // arrange
            val targetDate = LocalDate.of(2025, 1, 8)

            val reader = AggregatedHourlyMetricReader(dataSource, targetDate)
            reader.open(MetaDataInstanceFactory.createStepExecution().executionContext)

            // act
            val item = reader.read()

            // assert
            assertThat(item).isNull()
            reader.close()
        }

        @DisplayName("음수 likeCount도 정상적으로 집계한다")
        @Test
        fun shouldAggregateNegativeLikeCount() {
            // arrange
            val targetDate = LocalDate.of(2025, 1, 8)

            // 좋아요 취소로 인한 음수 likeCount
            insertHourlyMetric(toInstant(targetDate, 10), 100L, 10, 5, BigDecimal("100"))
            insertHourlyMetric(toInstant(targetDate, 14), 100L, 20, -2, BigDecimal("200"))

            val reader = AggregatedHourlyMetricReader(dataSource, targetDate)
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
            assertThat(items[0].likeCount).isEqualTo(3) // 5 + (-2) = 3
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
}
