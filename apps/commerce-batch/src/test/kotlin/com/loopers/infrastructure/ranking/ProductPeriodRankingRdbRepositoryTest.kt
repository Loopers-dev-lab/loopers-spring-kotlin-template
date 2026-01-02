package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.MvProductRankMonthly
import com.loopers.domain.ranking.MvProductRankWeekly
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.batch.job.enabled=false"])
@DisplayName("ProductPeriodRankingRdbRepository")
class ProductPeriodRankingRdbRepositoryTest @Autowired constructor(
    private val repository: ProductPeriodRankingRdbRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @BeforeEach
    fun setUp() {
        databaseCleanUp.afterPropertiesSet()
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("saveAllWeekly 메서드는")
    inner class SaveAllWeeklyTest {

        @DisplayName("주간 랭킹 데이터를 일괄 저장한다")
        @Test
        fun shouldSaveAllWeeklyRankings() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 2)
            val rankings = listOf(
                MvProductRankWeekly.create(
                    baseDate = baseDate,
                    rank = 1,
                    productId = 100L,
                    score = BigDecimal("1000.00"),
                ),
                MvProductRankWeekly.create(
                    baseDate = baseDate,
                    rank = 2,
                    productId = 200L,
                    score = BigDecimal("900.00"),
                ),
                MvProductRankWeekly.create(
                    baseDate = baseDate,
                    rank = 3,
                    productId = 300L,
                    score = BigDecimal("800.00"),
                ),
            )

            // act
            repository.saveAllWeekly(rankings)

            // assert
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE base_date = ?",
                Int::class.java,
                baseDate,
            )
            assertThat(count).isEqualTo(3)
        }

        @DisplayName("빈 리스트가 주어지면 저장하지 않는다")
        @Test
        fun shouldNotSave_whenEmptyList() {
            // arrange
            val rankings = emptyList<MvProductRankWeekly>()

            // act
            repository.saveAllWeekly(rankings)

            // assert
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly",
                Int::class.java,
            )
            assertThat(count).isEqualTo(0)
        }

        @DisplayName("100개의 랭킹 데이터를 일괄 저장한다")
        @Test
        fun shouldSave100Rankings() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 2)
            val rankings = (1..100).map { rank ->
                MvProductRankWeekly.create(
                    baseDate = baseDate,
                    rank = rank,
                    productId = rank.toLong() * 100,
                    score = BigDecimal(1000 - rank),
                )
            }

            // act
            repository.saveAllWeekly(rankings)

            // assert
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE base_date = ?",
                Int::class.java,
                baseDate,
            )
            assertThat(count).isEqualTo(100)
        }
    }

    @Nested
    @DisplayName("saveAllMonthly 메서드는")
    inner class SaveAllMonthlyTest {

        @DisplayName("월간 랭킹 데이터를 일괄 저장한다")
        @Test
        fun shouldSaveAllMonthlyRankings() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 2)
            val rankings = listOf(
                MvProductRankMonthly.create(
                    baseDate = baseDate,
                    rank = 1,
                    productId = 100L,
                    score = BigDecimal("5000.00"),
                ),
                MvProductRankMonthly.create(
                    baseDate = baseDate,
                    rank = 2,
                    productId = 200L,
                    score = BigDecimal("4500.00"),
                ),
            )

            // act
            repository.saveAllMonthly(rankings)

            // assert
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE base_date = ?",
                Int::class.java,
                baseDate,
            )
            assertThat(count).isEqualTo(2)
        }

        @DisplayName("빈 리스트가 주어지면 저장하지 않는다")
        @Test
        fun shouldNotSave_whenEmptyList() {
            // arrange
            val rankings = emptyList<MvProductRankMonthly>()

            // act
            repository.saveAllMonthly(rankings)

            // assert
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_monthly",
                Int::class.java,
            )
            assertThat(count).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("deleteWeeklyByBaseDate 메서드는")
    inner class DeleteWeeklyByBaseDateTest {

        @DisplayName("특정 날짜의 주간 랭킹을 삭제한다")
        @Test
        fun shouldDeleteWeeklyRankingsByBaseDate() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 2)
            val otherDate = LocalDate.of(2025, 1, 3)
            val rankings = listOf(
                MvProductRankWeekly.create(baseDate, 1, 100L, BigDecimal("1000.00")),
                MvProductRankWeekly.create(baseDate, 2, 200L, BigDecimal("900.00")),
            )
            val otherRankings = listOf(
                MvProductRankWeekly.create(otherDate, 1, 300L, BigDecimal("800.00")),
            )
            repository.saveAllWeekly(rankings)
            repository.saveAllWeekly(otherRankings)

            // act
            val deletedCount = repository.deleteWeeklyByBaseDate(baseDate)

            // assert
            assertThat(deletedCount).isEqualTo(2)
            val remainingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly",
                Int::class.java,
            )
            assertThat(remainingCount).isEqualTo(1)
        }

        @DisplayName("삭제할 데이터가 없으면 0을 반환한다")
        @Test
        fun shouldReturnZero_whenNoDataToDelete() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 2)

            // act
            val deletedCount = repository.deleteWeeklyByBaseDate(baseDate)

            // assert
            assertThat(deletedCount).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("deleteMonthlyByBaseDate 메서드는")
    inner class DeleteMonthlyByBaseDateTest {

        @DisplayName("특정 날짜의 월간 랭킹을 삭제한다")
        @Test
        fun shouldDeleteMonthlyRankingsByBaseDate() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 2)
            val otherDate = LocalDate.of(2025, 1, 3)
            val rankings = listOf(
                MvProductRankMonthly.create(baseDate, 1, 100L, BigDecimal("5000.00")),
                MvProductRankMonthly.create(baseDate, 2, 200L, BigDecimal("4500.00")),
                MvProductRankMonthly.create(baseDate, 3, 300L, BigDecimal("4000.00")),
            )
            val otherRankings = listOf(
                MvProductRankMonthly.create(otherDate, 1, 400L, BigDecimal("3500.00")),
            )
            repository.saveAllMonthly(rankings)
            repository.saveAllMonthly(otherRankings)

            // act
            val deletedCount = repository.deleteMonthlyByBaseDate(baseDate)

            // assert
            assertThat(deletedCount).isEqualTo(3)
            val remainingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_monthly",
                Int::class.java,
            )
            assertThat(remainingCount).isEqualTo(1)
        }

        @DisplayName("삭제할 데이터가 없으면 0을 반환한다")
        @Test
        fun shouldReturnZero_whenNoDataToDelete() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 2)

            // act
            val deletedCount = repository.deleteMonthlyByBaseDate(baseDate)

            // assert
            assertThat(deletedCount).isEqualTo(0)
        }
    }
}
