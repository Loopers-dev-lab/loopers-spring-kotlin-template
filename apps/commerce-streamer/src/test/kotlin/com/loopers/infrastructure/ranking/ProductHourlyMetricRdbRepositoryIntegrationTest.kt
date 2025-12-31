package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductHourlyMetricRepository
import com.loopers.domain.ranking.ProductHourlyMetricRow
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
@DisplayName("ProductHourlyMetricRdbRepository 통합 테스트")
class ProductHourlyMetricRdbRepositoryIntegrationTest @Autowired constructor(
    private val productHourlyMetricRepository: ProductHourlyMetricRepository,
    private val productHourlyMetricJpaRepository: ProductHourlyMetricJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("batchAccumulateCounts()")
    @Nested
    inner class BatchAccumulateCounts {

        @DisplayName("새로운 레코드를 삽입한다")
        @Test
        fun `inserts new record when no existing record`() {
            // given
            val statHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val row = ProductHourlyMetricRow(
                productId = 1L,
                statHour = statHour,
                viewCount = 10L,
                likeCount = 5L,
                orderCount = 2L,
                orderAmount = BigDecimal("1000.00"),
            )

            // when
            productHourlyMetricRepository.batchAccumulateCounts(listOf(row))

            // then
            val results = productHourlyMetricJpaRepository.findAll()
            assertThat(results).hasSize(1)

            val result = results[0]
            assertThat(result.productId).isEqualTo(1L)
            assertThat(result.viewCount).isEqualTo(10L)
            assertThat(result.likeCount).isEqualTo(5L)
            assertThat(result.orderCount).isEqualTo(2L)
            assertThat(result.orderAmount).isEqualByComparingTo(BigDecimal("1000.00"))
        }

        @DisplayName("기존 레코드가 있으면 값을 누적한다")
        @Test
        fun `accumulates values when record already exists`() {
            // given
            val statHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val firstRow = ProductHourlyMetricRow(
                productId = 1L,
                statHour = statHour,
                viewCount = 10L,
                likeCount = 5L,
                orderCount = 2L,
                orderAmount = BigDecimal("1000.00"),
            )
            productHourlyMetricRepository.batchAccumulateCounts(listOf(firstRow))

            val secondRow = ProductHourlyMetricRow(
                productId = 1L,
                statHour = statHour,
                viewCount = 20L,
                likeCount = 10L,
                orderCount = 3L,
                orderAmount = BigDecimal("2000.00"),
            )

            // when
            productHourlyMetricRepository.batchAccumulateCounts(listOf(secondRow))

            // then
            val results = productHourlyMetricJpaRepository.findAll()
            assertThat(results).hasSize(1)

            val result = results[0]
            assertThat(result.viewCount).isEqualTo(30L) // 10 + 20
            assertThat(result.likeCount).isEqualTo(15L) // 5 + 10
            assertThat(result.orderCount).isEqualTo(5L) // 2 + 3
            assertThat(result.orderAmount).isEqualByComparingTo(BigDecimal("3000.00")) // 1000 + 2000
        }

        @DisplayName("여러 상품의 레코드를 한 번에 삽입한다")
        @Test
        fun `inserts multiple products at once`() {
            // given
            val statHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val rows = listOf(
                ProductHourlyMetricRow(
                    productId = 1L,
                    statHour = statHour,
                    viewCount = 10L,
                    likeCount = 5L,
                    orderCount = 2L,
                    orderAmount = BigDecimal("1000.00"),
                ),
                ProductHourlyMetricRow(
                    productId = 2L,
                    statHour = statHour,
                    viewCount = 20L,
                    likeCount = 10L,
                    orderCount = 4L,
                    orderAmount = BigDecimal("2000.00"),
                ),
            )

            // when
            productHourlyMetricRepository.batchAccumulateCounts(rows)

            // then
            val results = productHourlyMetricJpaRepository.findAll()
            assertThat(results).hasSize(2)
            assertThat(results.map { it.productId }).containsExactlyInAnyOrder(1L, 2L)
        }

        @DisplayName("빈 목록을 전달하면 아무 작업도 하지 않는다")
        @Test
        fun `does nothing when given empty list`() {
            // when
            productHourlyMetricRepository.batchAccumulateCounts(emptyList())

            // then
            val results = productHourlyMetricJpaRepository.findAll()
            assertThat(results).isEmpty()
        }

        @DisplayName("likeCount에 음수 값을 누적할 수 있다")
        @Test
        fun `accumulates negative likeCount values`() {
            // given
            val statHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val firstRow = ProductHourlyMetricRow(
                productId = 1L,
                statHour = statHour,
                viewCount = 10L,
                likeCount = 5L,
                orderCount = 2L,
                orderAmount = BigDecimal("1000.00"),
            )
            productHourlyMetricRepository.batchAccumulateCounts(listOf(firstRow))

            // 좋아요 취소
            val secondRow = ProductHourlyMetricRow(
                productId = 1L,
                statHour = statHour,
                viewCount = 0L,
                likeCount = -3L,
                orderCount = 0L,
                orderAmount = BigDecimal.ZERO,
            )

            // when
            productHourlyMetricRepository.batchAccumulateCounts(listOf(secondRow))

            // then
            val results = productHourlyMetricJpaRepository.findAll()
            assertThat(results).hasSize(1)
            assertThat(results[0].likeCount).isEqualTo(2L) // 5 + (-3)
        }

        @DisplayName("서로 다른 시간 버킷에 개별 레코드가 생성된다")
        @Test
        fun `creates separate records for different hour buckets`() {
            // given
            val statHour1 = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val statHour2 = statHour1.plus(1, ChronoUnit.HOURS)

            val rows = listOf(
                ProductHourlyMetricRow(
                    productId = 1L,
                    statHour = statHour1,
                    viewCount = 10L,
                    likeCount = 5L,
                    orderCount = 2L,
                    orderAmount = BigDecimal("1000.00"),
                ),
                ProductHourlyMetricRow(
                    productId = 1L,
                    statHour = statHour2,
                    viewCount = 20L,
                    likeCount = 10L,
                    orderCount = 4L,
                    orderAmount = BigDecimal("2000.00"),
                ),
            )

            // when
            productHourlyMetricRepository.batchAccumulateCounts(rows)

            // then
            val results = productHourlyMetricJpaRepository.findAll()
            assertThat(results).hasSize(2)
            assertThat(results.map { it.productId }).containsOnly(1L)

            val sortedByViewCount = results.sortedBy { it.viewCount }
            assertThat(sortedByViewCount[0].viewCount).isEqualTo(10L)
            assertThat(sortedByViewCount[1].viewCount).isEqualTo(20L)
        }
    }

    @DisplayName("findAllByStatHour()")
    @Nested
    inner class FindAllByStatHour {

        @DisplayName("해당 시간 버킷의 모든 레코드를 조회한다")
        @Test
        fun `returns all records for given stat hour`() {
            // given
            val statHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val rows = listOf(
                ProductHourlyMetricRow(
                    productId = 1L,
                    statHour = statHour,
                    viewCount = 10L,
                    likeCount = 5L,
                    orderCount = 2L,
                    orderAmount = BigDecimal("1000.00"),
                ),
                ProductHourlyMetricRow(
                    productId = 2L,
                    statHour = statHour,
                    viewCount = 20L,
                    likeCount = 10L,
                    orderCount = 4L,
                    orderAmount = BigDecimal("2000.00"),
                ),
            )
            productHourlyMetricRepository.batchAccumulateCounts(rows)

            // when
            val results = productHourlyMetricRepository.findAllByStatHour(statHour)

            // then
            assertThat(results).hasSize(2)
            assertThat(results.map { it.productId }).containsExactlyInAnyOrder(1L, 2L)
        }

        @DisplayName("다른 시간 버킷의 레코드는 조회하지 않는다")
        @Test
        fun `does not return records from different stat hours`() {
            // given
            val statHour1 = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val statHour2 = statHour1.plus(1, ChronoUnit.HOURS)

            val rows = listOf(
                ProductHourlyMetricRow(
                    productId = 1L,
                    statHour = statHour1,
                    viewCount = 10L,
                    likeCount = 5L,
                    orderCount = 2L,
                    orderAmount = BigDecimal("1000.00"),
                ),
                ProductHourlyMetricRow(
                    productId = 2L,
                    statHour = statHour2,
                    viewCount = 20L,
                    likeCount = 10L,
                    orderCount = 4L,
                    orderAmount = BigDecimal("2000.00"),
                ),
            )
            productHourlyMetricRepository.batchAccumulateCounts(rows)

            // when
            val results = productHourlyMetricRepository.findAllByStatHour(statHour1)

            // then
            assertThat(results).hasSize(1)
            assertThat(results[0].productId).isEqualTo(1L)
        }

        @DisplayName("해당 시간 버킷에 레코드가 없으면 빈 목록을 반환한다")
        @Test
        fun `returns empty list when no records exist for given stat hour`() {
            // given
            val statHour = Instant.now().truncatedTo(ChronoUnit.HOURS)

            // when
            val results = productHourlyMetricRepository.findAllByStatHour(statHour)

            // then
            assertThat(results).isEmpty()
        }
    }
}
