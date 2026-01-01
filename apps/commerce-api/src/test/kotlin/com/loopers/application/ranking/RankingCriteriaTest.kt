package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingPeriod
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("RankingCriteria 테스트")
class RankingCriteriaTest {

    @DisplayName("FindRankings.toCommand 테스트")
    @Nested
    inner class ToCommand {

        @DisplayName("모든 값이 지정되면 해당 값으로 Command를 생성한다")
        @Test
        fun `creates Command with specified values`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = "daily",
                date = "20250115",
                page = 2,
                size = 30,
            )

            // when
            val command = criteria.toCommand()

            // then
            assertThat(command.period).isEqualTo(RankingPeriod.DAILY)
            assertThat(command.date).isEqualTo("20250115")
            assertThat(command.page).isEqualTo(2)
            assertThat(command.size).isEqualTo(30)
        }

        @DisplayName("period가 null이면 HOURLY로 변환된다")
        @Test
        fun `converts null period to HOURLY`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = null,
                date = null,
                page = 0,
                size = 20,
            )

            // when
            val command = criteria.toCommand()

            // then
            assertThat(command.period).isEqualTo(RankingPeriod.HOURLY)
        }

        @DisplayName("period가 hourly이면 HOURLY로 변환된다")
        @Test
        fun `converts hourly string to HOURLY enum`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = "hourly",
                date = null,
                page = 0,
                size = 20,
            )

            // when
            val command = criteria.toCommand()

            // then
            assertThat(command.period).isEqualTo(RankingPeriod.HOURLY)
        }

        @DisplayName("period가 daily이면 DAILY로 변환된다")
        @Test
        fun `converts daily string to DAILY enum`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = "daily",
                date = null,
                page = 0,
                size = 20,
            )

            // when
            val command = criteria.toCommand()

            // then
            assertThat(command.period).isEqualTo(RankingPeriod.DAILY)
        }

        @DisplayName("page가 null이면 기본값 0을 사용한다")
        @Test
        fun `uses default page 0 when null`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = "hourly",
                date = null,
                page = null,
                size = 20,
            )

            // when
            val command = criteria.toCommand()

            // then
            assertThat(command.page).isEqualTo(0)
        }

        @DisplayName("size가 null이면 기본값 20을 사용한다")
        @Test
        fun `uses default size 20 when null`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = "hourly",
                date = null,
                page = 0,
                size = null,
            )

            // when
            val command = criteria.toCommand()

            // then
            assertThat(command.size).isEqualTo(20)
        }

        @DisplayName("모든 값이 null이면 기본값으로 Command를 생성한다")
        @Test
        fun `creates Command with all defaults when all values are null`() {
            // given
            val criteria = RankingCriteria.FindRankings()

            // when
            val command = criteria.toCommand()

            // then
            assertThat(command.period).isEqualTo(RankingPeriod.HOURLY)
            assertThat(command.date).isNull()
            assertThat(command.page).isEqualTo(0)
            assertThat(command.size).isEqualTo(20)
        }

        @DisplayName("date가 지정되면 그대로 전달된다")
        @Test
        fun `passes date as is when specified`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = "hourly",
                date = "2025011514",
                page = 0,
                size = 20,
            )

            // when
            val command = criteria.toCommand()

            // then
            assertThat(command.date).isEqualTo("2025011514")
        }
    }
}
