package com.loopers.batch.job.ranking.step

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig
import com.loopers.domain.ranking.ProductHourlyMetric
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
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.sql.DataSource

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.batch.job.name=${WeeklyRankingJobConfig.JOB_NAME}"])
@DisplayName("HourlyMetricReader")
class HourlyMetricReaderTest @Autowired constructor(
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
    @DisplayName("시간별 메트릭 조회는")
    inner class HourlyWindowTest {

        @DisplayName("baseDateTime 기준 현재 시간과 이전 시간 데이터를 조회한다")
        @Test
        fun shouldReadCurrentAndPreviousHourData() {
            // arrange
            // baseDateTime=2025-01-08T14:30:00 -> 현재 시간 버킷: 14:00, 이전 시간 버킷: 13:00
            val baseDateTime = Instant.parse("2025-01-08T05:30:00Z") // UTC (KST 14:30)
            val currentHour = baseDateTime.truncatedTo(ChronoUnit.HOURS) // 14:00 KST
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS) // 13:00 KST

            // 범위 내 데이터 (현재 시간 버킷)
            insertMetric(currentHour, 100L, 10, 1, BigDecimal("100"))
            insertMetric(currentHour, 200L, 20, 2, BigDecimal("200"))

            // 범위 내 데이터 (이전 시간 버킷)
            insertMetric(previousHour, 100L, 30, 3, BigDecimal("300"))

            // 범위 밖 데이터 (2시간 전)
            val twoHoursAgo = currentHour.minus(2, ChronoUnit.HOURS)
            insertMetric(twoHoursAgo, 100L, 40, 4, BigDecimal("400"))

            // 범위 밖 데이터 (1시간 후)
            val oneHourLater = currentHour.plus(1, ChronoUnit.HOURS)
            insertMetric(oneHourLater, 100L, 50, 5, BigDecimal("500"))

            val reader = HourlyMetricReader(dataSource, baseDateTime)
            reader.open(MetaDataInstanceFactory.createStepExecution().executionContext)

            // act
            val items = mutableListOf<ProductHourlyMetric>()
            var item = reader.read()
            while (item != null) {
                items.add(item)
                item = reader.read()
            }
            reader.close()

            // assert
            assertThat(items).hasSize(3)
            assertThat(items.map { it.statHour }).containsExactlyInAnyOrder(
                currentHour,
                currentHour,
                previousHour,
            )
        }

        @DisplayName("stat_hour, product_id 순으로 정렬하여 조회한다")
        @Test
        fun shouldSortByStatHourAndProductId() {
            // arrange
            val baseDateTime = Instant.parse("2025-01-08T05:30:00Z") // UTC (KST 14:30)
            val currentHour = baseDateTime.truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)

            // 순서 무작위로 삽입
            insertMetric(currentHour, 200L, 10, 1, BigDecimal("100"))
            insertMetric(previousHour, 100L, 20, 2, BigDecimal("200"))
            insertMetric(currentHour, 100L, 30, 3, BigDecimal("300"))

            val reader = HourlyMetricReader(dataSource, baseDateTime)
            reader.open(MetaDataInstanceFactory.createStepExecution().executionContext)

            // act
            val items = mutableListOf<ProductHourlyMetric>()
            var item = reader.read()
            while (item != null) {
                items.add(item)
                item = reader.read()
            }
            reader.close()

            // assert
            assertThat(items).hasSize(3)
            // 정렬: stat_hour ASC, product_id ASC
            // 1. previousHour, 100
            // 2. currentHour, 100
            // 3. currentHour, 200
            assertThat(items[0].statHour).isEqualTo(previousHour)
            assertThat(items[0].productId).isEqualTo(100L)
            assertThat(items[1].statHour).isEqualTo(currentHour)
            assertThat(items[1].productId).isEqualTo(100L)
            assertThat(items[2].statHour).isEqualTo(currentHour)
            assertThat(items[2].productId).isEqualTo(200L)
        }

        @DisplayName("데이터가 없는 경우 빈 결과를 반환한다")
        @Test
        fun shouldReturnEmpty_whenNoData() {
            // arrange
            val baseDateTime = Instant.parse("2025-01-08T05:30:00Z")

            val reader = HourlyMetricReader(dataSource, baseDateTime)
            reader.open(MetaDataInstanceFactory.createStepExecution().executionContext)

            // act
            val item = reader.read()

            // assert
            assertThat(item).isNull()
            reader.close()
        }

        @DisplayName("현재 시간 버킷만 있는 경우 현재 시간 데이터만 조회한다")
        @Test
        fun shouldReadOnlyCurrentHour_whenPreviousHourDataNotExists() {
            // arrange
            val baseDateTime = Instant.parse("2025-01-08T05:30:00Z")
            val currentHour = baseDateTime.truncatedTo(ChronoUnit.HOURS)

            insertMetric(currentHour, 100L, 10, 1, BigDecimal("100"))
            insertMetric(currentHour, 200L, 20, 2, BigDecimal("200"))

            val reader = HourlyMetricReader(dataSource, baseDateTime)
            reader.open(MetaDataInstanceFactory.createStepExecution().executionContext)

            // act
            val items = mutableListOf<ProductHourlyMetric>()
            var item = reader.read()
            while (item != null) {
                items.add(item)
                item = reader.read()
            }
            reader.close()

            // assert
            assertThat(items).hasSize(2)
            assertThat(items.map { it.statHour }).allMatch { it == currentHour }
        }

        @DisplayName("이전 시간 버킷만 있는 경우 이전 시간 데이터만 조회한다")
        @Test
        fun shouldReadOnlyPreviousHour_whenCurrentHourDataNotExists() {
            // arrange
            val baseDateTime = Instant.parse("2025-01-08T05:30:00Z")
            val currentHour = baseDateTime.truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)

            insertMetric(previousHour, 100L, 10, 1, BigDecimal("100"))

            val reader = HourlyMetricReader(dataSource, baseDateTime)
            reader.open(MetaDataInstanceFactory.createStepExecution().executionContext)

            // act
            val items = mutableListOf<ProductHourlyMetric>()
            var item = reader.read()
            while (item != null) {
                items.add(item)
                item = reader.read()
            }
            reader.close()

            // assert
            assertThat(items).hasSize(1)
            assertThat(items[0].statHour).isEqualTo(previousHour)
            assertThat(items[0].productId).isEqualTo(100L)
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
