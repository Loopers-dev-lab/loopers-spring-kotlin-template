package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("RankingCommand 테스트")
class RankingCommandTest {

    @DisplayName("FindRankings 테스트")
    @Nested
    inner class FindRankingsTest {

        @DisplayName("유효한 page와 size로 FindRankings를 생성한다")
        @Test
        fun `creates FindRankings with valid page and size`() {
            // given
            val period = RankingPeriod.HOURLY
            val date = "2025011514"
            val page = 0
            val size = 20

            // when
            val command = RankingCommand.FindRankings(
                period = period,
                date = date,
                page = page,
                size = size,
            )

            // then
            assertThat(command.period).isEqualTo(RankingPeriod.HOURLY)
            assertThat(command.date).isEqualTo("2025011514")
            assertThat(command.page).isEqualTo(0)
            assertThat(command.size).isEqualTo(20)
        }

        @DisplayName("date가 null인 경우에도 FindRankings를 생성한다")
        @Test
        fun `creates FindRankings with null date`() {
            // given
            val period = RankingPeriod.DAILY
            val page = 1
            val size = 50

            // when
            val command = RankingCommand.FindRankings(
                period = period,
                date = null,
                page = page,
                size = size,
            )

            // then
            assertThat(command.period).isEqualTo(RankingPeriod.DAILY)
            assertThat(command.date).isNull()
            assertThat(command.page).isEqualTo(1)
            assertThat(command.size).isEqualTo(50)
        }

        @DisplayName("page validation 테스트")
        @Nested
        inner class PageValidation {

            @ParameterizedTest
            @ValueSource(ints = [-1, -10, -100])
            @DisplayName("page가 음수이면 IllegalArgumentException을 던진다")
            fun `throws IllegalArgumentException when page is negative`(invalidPage: Int) {
                // when
                val exception = assertThrows<IllegalArgumentException> {
                    RankingCommand.FindRankings(
                        period = RankingPeriod.HOURLY,
                        date = null,
                        page = invalidPage,
                        size = 20,
                    )
                }

                // then
                assertThat(exception.message).contains("page")
                assertThat(exception.message).contains(invalidPage.toString())
            }

            @ParameterizedTest
            @ValueSource(ints = [0, 1, 10, 100, 1000])
            @DisplayName("page가 0 이상이면 FindRankings를 생성한다")
            fun `creates FindRankings when page is 0 or positive`(validPage: Int) {
                // when
                val command = RankingCommand.FindRankings(
                    period = RankingPeriod.HOURLY,
                    date = null,
                    page = validPage,
                    size = 20,
                )

                // then
                assertThat(command.page).isEqualTo(validPage)
            }
        }

        @DisplayName("size validation 테스트")
        @Nested
        inner class SizeValidation {

            @ParameterizedTest
            @ValueSource(ints = [0, -1, -10])
            @DisplayName("size가 1 미만이면 IllegalArgumentException을 던진다")
            fun `throws IllegalArgumentException when size is less than 1`(invalidSize: Int) {
                // when
                val exception = assertThrows<IllegalArgumentException> {
                    RankingCommand.FindRankings(
                        period = RankingPeriod.HOURLY,
                        date = null,
                        page = 0,
                        size = invalidSize,
                    )
                }

                // then
                assertThat(exception.message).contains("size")
                assertThat(exception.message).contains(invalidSize.toString())
            }

            @ParameterizedTest
            @ValueSource(ints = [101, 200, 1000])
            @DisplayName("size가 100을 초과하면 IllegalArgumentException을 던진다")
            fun `throws IllegalArgumentException when size exceeds 100`(invalidSize: Int) {
                // when
                val exception = assertThrows<IllegalArgumentException> {
                    RankingCommand.FindRankings(
                        period = RankingPeriod.HOURLY,
                        date = null,
                        page = 0,
                        size = invalidSize,
                    )
                }

                // then
                assertThat(exception.message).contains("size")
                assertThat(exception.message).contains(invalidSize.toString())
            }

            @ParameterizedTest
            @ValueSource(ints = [1, 10, 20, 50, 100])
            @DisplayName("size가 1~100 범위이면 FindRankings를 생성한다")
            fun `creates FindRankings when size is between 1 and 100`(validSize: Int) {
                // when
                val command = RankingCommand.FindRankings(
                    period = RankingPeriod.HOURLY,
                    date = null,
                    page = 0,
                    size = validSize,
                )

                // then
                assertThat(command.size).isEqualTo(validSize)
            }
        }
    }
}
