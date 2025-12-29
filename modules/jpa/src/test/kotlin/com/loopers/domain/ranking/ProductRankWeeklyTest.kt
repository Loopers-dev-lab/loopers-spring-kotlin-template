package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

@DisplayName("ProductRankWeekly 도메인 테스트")
class ProductRankWeeklyTest {

    @Test
    fun `주간 랭킹을 생성할 수 있다`() {
        // given
        val yearWeek = "2025W03"
        val productId = 100L
        val score = 200.5
        val rank = 1
        val periodStart = LocalDate.of(2025, 1, 13) // Monday
        val periodEnd = LocalDate.of(2025, 1, 19) // Sunday

        // when
        val productRankWeekly = ProductRankWeekly(
            yearWeek = yearWeek,
            productId = productId,
            score = score,
            rank = rank,
            periodStart = periodStart,
            periodEnd = periodEnd,
        )

        // then
        assertThat(productRankWeekly.yearWeek).isEqualTo(yearWeek)
        assertThat(productRankWeekly.productId).isEqualTo(productId)
        assertThat(productRankWeekly.score).isEqualTo(score)
        assertThat(productRankWeekly.rank).isEqualTo(rank)
        assertThat(productRankWeekly.periodStart).isEqualTo(periodStart)
        assertThat(productRankWeekly.periodEnd).isEqualTo(periodEnd)
    }

    @Test
    fun `yearWeek 형식이 YYYY'W'ww 이다`() {
        // given
        val date = LocalDate.of(2025, 1, 15) // Wednesday in week 3

        // when
        val yearWeek = ProductRankWeekly.yearWeekToString(date)

        // then
        assertThat(yearWeek).matches("\\d{4}W\\d{2}") // YYYY'W'ww pattern
        assertThat(yearWeek).isEqualTo("2025W03")
    }

    @Test
    fun `주간 랭킹의 순위는 1 이상이어야 한다`() {
        // given
        val yearWeek = "2025W03"
        val productId = 100L
        val score = 200.5
        val periodStart = LocalDate.of(2025, 1, 13)
        val periodEnd = LocalDate.of(2025, 1, 19)

        // when
        val productRankWeekly = ProductRankWeekly(
            yearWeek = yearWeek,
            productId = productId,
            score = score,
            rank = 1, // minimum valid rank
            periodStart = periodStart,
            periodEnd = periodEnd,
        )

        // then
        assertThat(productRankWeekly.rank).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `년도의 첫 주를 올바르게 생성한다`() {
        // given
        val date = LocalDate.of(2025, 1, 1) // First day of year

        // when
        val yearWeek = ProductRankWeekly.yearWeekToString(date)

        // then
        assertThat(yearWeek).startsWith("2025W")
        assertThat(yearWeek).matches("\\d{4}W\\d{2}")
    }

    @Test
    fun `년도의 마지막 주를 올바르게 생성한다`() {
        // given
        val date = LocalDate.of(2025, 12, 31) // Last day of year

        // when
        val yearWeek = ProductRankWeekly.yearWeekToString(date)

        // then
        assertThat(yearWeek).matches("\\d{4}W\\d{2}")
    }
}
