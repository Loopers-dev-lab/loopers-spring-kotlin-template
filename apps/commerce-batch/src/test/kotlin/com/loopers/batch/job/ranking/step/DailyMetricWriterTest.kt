package com.loopers.batch.job.ranking.step

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig
import com.loopers.domain.ranking.ProductDailyMetric
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.batch.item.Chunk
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.sql.Date
import java.time.LocalDate

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.batch.job.name=${WeeklyRankingJobConfig.JOB_NAME}"])
@DisplayName("DailyMetricWriter")
class DailyMetricWriterTest @Autowired constructor(
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    private lateinit var writer: DailyMetricWriter

    @BeforeEach
    fun setUp() {
        databaseCleanUp.afterPropertiesSet()
        databaseCleanUp.truncateAllTables()
        writer = DailyMetricWriter(jdbcTemplate)
    }

    @Nested
    @DisplayName("UPSERT 동작은")
    inner class UpsertBehaviorTest {

        @DisplayName("새로운 데이터를 INSERT 한다")
        @Test
        fun shouldInsertNewData() {
            // arrange
            val statDate = LocalDate.of(2025, 1, 8)
            val metrics = listOf(
                ProductDailyMetric(
                    statDate = statDate,
                    productId = 100L,
                    viewCount = 10,
                    likeCount = 1,
                    orderAmount = BigDecimal("100.00"),
                ),
                ProductDailyMetric(
                    statDate = statDate,
                    productId = 200L,
                    viewCount = 20,
                    likeCount = 2,
                    orderAmount = BigDecimal("200.00"),
                ),
            )

            // act
            writer.write(Chunk(metrics))

            // assert
            val results = queryDailyMetrics(statDate)
            assertThat(results).hasSize(2)

            val product100 = results.find { it["product_id"] == 100L }!!
            assertThat(product100["view_count"]).isEqualTo(10L)
            assertThat(product100["like_count"]).isEqualTo(1L)
            assertThat((product100["order_amount"] as BigDecimal)).isEqualByComparingTo(BigDecimal("100.00"))

            val product200 = results.find { it["product_id"] == 200L }!!
            assertThat(product200["view_count"]).isEqualTo(20L)
            assertThat(product200["like_count"]).isEqualTo(2L)
            assertThat((product200["order_amount"] as BigDecimal)).isEqualByComparingTo(BigDecimal("200.00"))
        }

        @DisplayName("기존 데이터가 있으면 UPDATE 한다")
        @Test
        fun shouldUpdateExistingData() {
            // arrange
            val statDate = LocalDate.of(2025, 1, 8)

            // 기존 데이터 삽입
            insertDailyMetric(statDate, 100L, 10, 1, BigDecimal("100.00"))

            // 새로운 값으로 UPSERT
            val metrics = listOf(
                ProductDailyMetric(
                    statDate = statDate,
                    productId = 100L,
                    viewCount = 50,
                    likeCount = 5,
                    orderAmount = BigDecimal("500.00"),
                ),
            )

            // act
            writer.write(Chunk(metrics))

            // assert
            val results = queryDailyMetrics(statDate)
            assertThat(results).hasSize(1)

            val product100 = results[0]
            assertThat(product100["product_id"]).isEqualTo(100L)
            assertThat(product100["view_count"]).isEqualTo(50L) // 업데이트됨
            assertThat(product100["like_count"]).isEqualTo(5L) // 업데이트됨
            assertThat((product100["order_amount"] as BigDecimal)).isEqualByComparingTo(BigDecimal("500.00"))
        }

        @DisplayName("INSERT와 UPDATE가 혼합되어 동작한다")
        @Test
        fun shouldHandleMixedInsertAndUpdate() {
            // arrange
            val statDate = LocalDate.of(2025, 1, 8)

            // 기존 데이터 삽입 (product_id=100)
            insertDailyMetric(statDate, 100L, 10, 1, BigDecimal("100.00"))

            // product_id=100: UPDATE, product_id=200: INSERT
            val metrics = listOf(
                ProductDailyMetric(
                    statDate = statDate,
                    productId = 100L,
                    viewCount = 50,
                    likeCount = 5,
                    orderAmount = BigDecimal("500.00"),
                ),
                ProductDailyMetric(
                    statDate = statDate,
                    productId = 200L,
                    viewCount = 20,
                    likeCount = 2,
                    orderAmount = BigDecimal("200.00"),
                ),
            )

            // act
            writer.write(Chunk(metrics))

            // assert
            val results = queryDailyMetrics(statDate)
            assertThat(results).hasSize(2)

            val product100 = results.find { it["product_id"] == 100L }!!
            assertThat(product100["view_count"]).isEqualTo(50L) // UPDATE

            val product200 = results.find { it["product_id"] == 200L }!!
            assertThat(product200["view_count"]).isEqualTo(20L) // INSERT
        }

        @DisplayName("빈 Chunk는 아무 동작도 하지 않는다")
        @Test
        fun shouldDoNothing_whenChunkIsEmpty() {
            // arrange
            val statDate = LocalDate.of(2025, 1, 8)
            insertDailyMetric(statDate, 100L, 10, 1, BigDecimal("100.00"))

            val emptyMetrics = emptyList<ProductDailyMetric>()

            // act
            writer.write(Chunk(emptyMetrics))

            // assert
            val results = queryDailyMetrics(statDate)
            assertThat(results).hasSize(1) // 기존 데이터 유지
            assertThat(results[0]["view_count"]).isEqualTo(10L) // 변경 없음
        }

        @DisplayName("여러 번 실행해도 최종 값으로 유지된다 (멱등성)")
        @Test
        fun shouldBeIdempotent() {
            // arrange
            val statDate = LocalDate.of(2025, 1, 8)
            val metrics = listOf(
                ProductDailyMetric(
                    statDate = statDate,
                    productId = 100L,
                    viewCount = 50,
                    likeCount = 5,
                    orderAmount = BigDecimal("500.00"),
                ),
            )

            // act - 3번 연속 실행
            writer.write(Chunk(metrics))
            writer.write(Chunk(metrics))
            writer.write(Chunk(metrics))

            // assert
            val results = queryDailyMetrics(statDate)
            assertThat(results).hasSize(1) // 중복 없음
            assertThat(results[0]["view_count"]).isEqualTo(50L)
            assertThat(results[0]["like_count"]).isEqualTo(5L)
        }
    }

    @Nested
    @DisplayName("날짜별 격리는")
    inner class DateIsolationTest {

        @DisplayName("서로 다른 날짜의 데이터는 독립적으로 저장된다")
        @Test
        fun shouldIsolateDifferentDates() {
            // arrange
            val date1 = LocalDate.of(2025, 1, 8)
            val date2 = LocalDate.of(2025, 1, 9)

            val metricsDate1 = listOf(
                ProductDailyMetric(
                    statDate = date1,
                    productId = 100L,
                    viewCount = 10,
                    likeCount = 1,
                    orderAmount = BigDecimal("100.00"),
                ),
            )
            val metricsDate2 = listOf(
                ProductDailyMetric(
                    statDate = date2,
                    productId = 100L,
                    viewCount = 20,
                    likeCount = 2,
                    orderAmount = BigDecimal("200.00"),
                ),
            )

            // act
            writer.write(Chunk(metricsDate1))
            writer.write(Chunk(metricsDate2))

            // assert
            val resultsDate1 = queryDailyMetrics(date1)
            val resultsDate2 = queryDailyMetrics(date2)

            assertThat(resultsDate1).hasSize(1)
            assertThat(resultsDate1[0]["view_count"]).isEqualTo(10L)

            assertThat(resultsDate2).hasSize(1)
            assertThat(resultsDate2[0]["view_count"]).isEqualTo(20L)
        }
    }

    private fun insertDailyMetric(
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

    private fun queryDailyMetrics(statDate: LocalDate): List<Map<String, Any>> {
        return jdbcTemplate.queryForList(
            """
            SELECT product_id, view_count, like_count, order_amount
            FROM product_daily_metric
            WHERE stat_date = ?
            ORDER BY product_id
            """.trimIndent(),
            Date.valueOf(statDate),
        )
    }
}
