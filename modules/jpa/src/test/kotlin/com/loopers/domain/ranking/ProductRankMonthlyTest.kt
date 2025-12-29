package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("ProductRankMonthly 도메인 테스트")
class ProductRankMonthlyTest {

    @Test
    fun `월간 랭킹을 생성할 수 있다`() {
        // given
        val yearMonth = "202501"
        val productId = 100L
        val score = 300.5
        val rank = 1
        val periodStart = LocalDate.of(2025, 1, 1)
        val periodEnd = LocalDate.of(2025, 1, 31)

        // when
        val productRankMonthly = ProductRankMonthly(
            yearMonth = yearMonth,
            productId = productId,
            score = score,
            rank = rank,
            periodStart = periodStart,
            periodEnd = periodEnd,
        )

        // then
        assertThat(productRankMonthly.yearMonth).isEqualTo(yearMonth)
        assertThat(productRankMonthly.productId).isEqualTo(productId)
        assertThat(productRankMonthly.score).isEqualTo(score)
        assertThat(productRankMonthly.rank).isEqualTo(rank)
        assertThat(productRankMonthly.periodStart).isEqualTo(periodStart)
        assertThat(productRankMonthly.periodEnd).isEqualTo(periodEnd)
    }

    @Test
    fun `yearMonth 형식이 yyyyMM 이다`() {
        // given
        val yearMonth = YearMonth.of(2025, 1)

        // when
        val yearMonthString = ProductRankMonthly.yearMonthToString(yearMonth)

        // then
        assertThat(yearMonthString).matches("\\d{6}") // yyyyMM pattern
        assertThat(yearMonthString).isEqualTo("202501")
        assertThat(yearMonthString).hasSize(6)
    }

    @Test
    fun `잘못된 yearMonth 형식은 예외를 발생시킨다`() {
        // given
        val invalidYearMonth1 = "2025-01" // wrong format
        val invalidYearMonth2 = "25-01" // too short
        val invalidYearMonth3 = "202513" // invalid month

        // when & then
        assertThatThrownBy {
            ProductRankMonthly(
                yearMonth = invalidYearMonth1,
                productId = 100L,
                score = 100.0,
                rank = 1,
                periodStart = LocalDate.of(2025, 1, 1),
                periodEnd = LocalDate.of(2025, 1, 31),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid yearMonth format")

        assertThatThrownBy {
            ProductRankMonthly(
                yearMonth = invalidYearMonth2,
                productId = 100L,
                score = 100.0,
                rank = 1,
                periodStart = LocalDate.of(2025, 1, 1),
                periodEnd = LocalDate.of(2025, 1, 31),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid yearMonth format")

        assertThatThrownBy {
            ProductRankMonthly(
                yearMonth = invalidYearMonth3,
                productId = 100L,
                score = 100.0,
                rank = 1,
                periodStart = LocalDate.of(2025, 1, 1),
                periodEnd = LocalDate.of(2025, 1, 31),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid yearMonth")
    }

    @Test
    fun `월간 랭킹의 순위는 1 이상이어야 한다`() {
        // given
        val yearMonth = "202501"
        val productId = 100L
        val score = 300.5
        val periodStart = LocalDate.of(2025, 1, 1)
        val periodEnd = LocalDate.of(2025, 1, 31)

        // when & then - rank = 0 (invalid)
        assertThatThrownBy {
            ProductRankMonthly(
                yearMonth = yearMonth,
                productId = productId,
                score = score,
                rank = 0,
                periodStart = periodStart,
                periodEnd = periodEnd,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Rank must be positive")

        // when & then - rank = -1 (invalid)
        assertThatThrownBy {
            ProductRankMonthly(
                yearMonth = yearMonth,
                productId = productId,
                score = score,
                rank = -1,
                periodStart = periodStart,
                periodEnd = periodEnd,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Rank must be positive")
    }

    @Test
    fun `YearMonth를 String으로 변환할 수 있다`() {
        // given
        val yearMonth = YearMonth.of(2025, 12)

        // when
        val yearMonthString = ProductRankMonthly.yearMonthToString(yearMonth)

        // then
        assertThat(yearMonthString).isEqualTo("202512")
    }

    @Test
    fun `String을 YearMonth로 변환할 수 있다`() {
        // given
        val yearMonthString = "202501"

        // when
        val yearMonth = ProductRankMonthly.stringToYearMonth(yearMonthString)

        // then
        assertThat(yearMonth).isEqualTo(YearMonth.of(2025, 1))
    }

    @Test
    fun `기간 시작일이 종료일보다 뒤일 수 없다`() {
        // given
        val yearMonth = "202501"
        val productId = 100L
        val score = 300.5
        val rank = 1
        val periodStart = LocalDate.of(2025, 1, 31) // 끝
        val periodEnd = LocalDate.of(2025, 1, 1) // 시작 (잘못됨)

        // when & then
        assertThatThrownBy {
            ProductRankMonthly(
                yearMonth = yearMonth,
                productId = productId,
                score = score,
                rank = rank,
                periodStart = periodStart,
                periodEnd = periodEnd,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Period start")
            .hasMessageContaining("must not be after period end")
    }

    @Test
    fun `다양한 월에 대해 yearMonth 변환이 올바르게 동작한다`() {
        // January
        assertThat(ProductRankMonthly.yearMonthToString(YearMonth.of(2025, 1))).isEqualTo("202501")

        // February
        assertThat(ProductRankMonthly.yearMonthToString(YearMonth.of(2025, 2))).isEqualTo("202502")

        // December
        assertThat(ProductRankMonthly.yearMonthToString(YearMonth.of(2025, 12))).isEqualTo("202512")
    }
}
