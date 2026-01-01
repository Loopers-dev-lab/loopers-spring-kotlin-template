package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductDailyMetric
import com.loopers.domain.ranking.ProductDailyMetricRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
@DisplayName("ProductDailyMetricRdbRepository 통합 테스트")
class ProductDailyMetricRdbRepositoryIntegrationTest @Autowired constructor(
    private val productDailyMetricRepository: ProductDailyMetricRepository,
    private val productDailyMetricJpaRepository: ProductDailyMetricJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("upsertFromHourly()")
    @Nested
    inner class UpsertFromHourly {

        @DisplayName("새로운 레코드를 삽입한다")
        @Test
        fun `inserts new record when no existing record`() {
            // given
            val statDate = LocalDate.now()
            val metric = ProductDailyMetric.create(
                statDate = statDate,
                productId = 1L,
                viewCount = 100L,
                likeCount = 50L,
                orderAmount = BigDecimal("10000.00"),
            )

            // when
            productDailyMetricRepository.upsertFromHourly(listOf(metric))

            // then
            val results = productDailyMetricJpaRepository.findAll()
            assertThat(results).hasSize(1)

            val result = results[0]
            assertThat(result.productId).isEqualTo(1L)
            assertThat(result.viewCount).isEqualTo(100L)
            assertThat(result.likeCount).isEqualTo(50L)
            assertThat(result.orderAmount).isEqualByComparingTo(BigDecimal("10000.00"))
        }

        @DisplayName("기존 레코드가 있으면 값을 덮어쓴다")
        @Test
        fun `overwrites values when record already exists`() {
            // given
            val statDate = LocalDate.now()
            val firstMetric = ProductDailyMetric.create(
                statDate = statDate,
                productId = 1L,
                viewCount = 100L,
                likeCount = 50L,
                orderAmount = BigDecimal("10000.00"),
            )
            productDailyMetricRepository.upsertFromHourly(listOf(firstMetric))

            val secondMetric = ProductDailyMetric.create(
                statDate = statDate,
                productId = 1L,
                viewCount = 200L,
                likeCount = 100L,
                orderAmount = BigDecimal("20000.00"),
            )

            // when
            productDailyMetricRepository.upsertFromHourly(listOf(secondMetric))

            // then
            val results = productDailyMetricJpaRepository.findAll()
            assertThat(results).hasSize(1)

            val result = results[0]
            assertThat(result.viewCount).isEqualTo(200L) // 덮어쓰기
            assertThat(result.likeCount).isEqualTo(100L) // 덮어쓰기
            assertThat(result.orderAmount).isEqualByComparingTo(BigDecimal("20000.00")) // 덮어쓰기
        }

        @DisplayName("여러 상품의 레코드를 한 번에 삽입한다")
        @Test
        fun `inserts multiple products at once`() {
            // given
            val statDate = LocalDate.now()
            val metrics = listOf(
                ProductDailyMetric.create(
                    statDate = statDate,
                    productId = 1L,
                    viewCount = 100L,
                    likeCount = 50L,
                    orderAmount = BigDecimal("10000.00"),
                ),
                ProductDailyMetric.create(
                    statDate = statDate,
                    productId = 2L,
                    viewCount = 200L,
                    likeCount = 100L,
                    orderAmount = BigDecimal("20000.00"),
                ),
            )

            // when
            productDailyMetricRepository.upsertFromHourly(metrics)

            // then
            val results = productDailyMetricJpaRepository.findAll()
            assertThat(results).hasSize(2)
            assertThat(results.map { it.productId }).containsExactlyInAnyOrder(1L, 2L)
        }

        @DisplayName("빈 목록을 전달하면 아무 작업도 하지 않는다")
        @Test
        fun `does nothing when given empty list`() {
            // when
            productDailyMetricRepository.upsertFromHourly(emptyList())

            // then
            val results = productDailyMetricJpaRepository.findAll()
            assertThat(results).isEmpty()
        }

        @DisplayName("서로 다른 날짜에 개별 레코드가 생성된다")
        @Test
        fun `creates separate records for different dates`() {
            // given
            val statDate1 = LocalDate.now()
            val statDate2 = statDate1.plusDays(1)

            val metrics = listOf(
                ProductDailyMetric.create(
                    statDate = statDate1,
                    productId = 1L,
                    viewCount = 100L,
                    likeCount = 50L,
                    orderAmount = BigDecimal("10000.00"),
                ),
                ProductDailyMetric.create(
                    statDate = statDate2,
                    productId = 1L,
                    viewCount = 200L,
                    likeCount = 100L,
                    orderAmount = BigDecimal("20000.00"),
                ),
            )

            // when
            productDailyMetricRepository.upsertFromHourly(metrics)

            // then
            val results = productDailyMetricJpaRepository.findAll()
            assertThat(results).hasSize(2)
            assertThat(results.map { it.productId }).containsOnly(1L)

            val sortedByViewCount = results.sortedBy { it.viewCount }
            assertThat(sortedByViewCount[0].viewCount).isEqualTo(100L)
            assertThat(sortedByViewCount[1].viewCount).isEqualTo(200L)
        }
    }

    @DisplayName("findAllByStatDate()")
    @Nested
    inner class FindAllByStatDate {

        @DisplayName("해당 날짜의 모든 레코드를 조회한다")
        @Test
        fun `returns all records for given stat date`() {
            // given
            val statDate = LocalDate.now()
            val metrics = listOf(
                ProductDailyMetric.create(
                    statDate = statDate,
                    productId = 1L,
                    viewCount = 100L,
                    likeCount = 50L,
                    orderAmount = BigDecimal("10000.00"),
                ),
                ProductDailyMetric.create(
                    statDate = statDate,
                    productId = 2L,
                    viewCount = 200L,
                    likeCount = 100L,
                    orderAmount = BigDecimal("20000.00"),
                ),
            )
            productDailyMetricRepository.upsertFromHourly(metrics)

            // when
            val results = productDailyMetricRepository.findAllByStatDate(statDate)

            // then
            assertThat(results).hasSize(2)
            assertThat(results.map { it.productId }).containsExactlyInAnyOrder(1L, 2L)
        }

        @DisplayName("다른 날짜의 레코드는 조회하지 않는다")
        @Test
        fun `does not return records from different dates`() {
            // given
            val statDate1 = LocalDate.now()
            val statDate2 = statDate1.plusDays(1)

            val metrics = listOf(
                ProductDailyMetric.create(
                    statDate = statDate1,
                    productId = 1L,
                    viewCount = 100L,
                    likeCount = 50L,
                    orderAmount = BigDecimal("10000.00"),
                ),
                ProductDailyMetric.create(
                    statDate = statDate2,
                    productId = 2L,
                    viewCount = 200L,
                    likeCount = 100L,
                    orderAmount = BigDecimal("20000.00"),
                ),
            )
            productDailyMetricRepository.upsertFromHourly(metrics)

            // when
            val results = productDailyMetricRepository.findAllByStatDate(statDate1)

            // then
            assertThat(results).hasSize(1)
            assertThat(results[0].productId).isEqualTo(1L)
        }

        @DisplayName("해당 날짜에 레코드가 없으면 빈 목록을 반환한다")
        @Test
        fun `returns empty list when no records exist for given date`() {
            // given
            val statDate = LocalDate.now()

            // when
            val results = productDailyMetricRepository.findAllByStatDate(statDate)

            // then
            assertThat(results).isEmpty()
        }
    }
}
