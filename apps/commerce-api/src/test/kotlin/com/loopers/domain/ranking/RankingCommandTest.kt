package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

@DisplayName("RankingCommand 테스트")
class RankingCommandTest {

    private val seoulZone = ZoneId.of("Asia/Seoul")

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

    @DisplayName("toQuery 테스트")
    @Nested
    inner class ToQuery {

        @DisplayName("date가 null이면 clock.instant()를 사용한다")
        @Test
        fun `toQuery with null date uses clock instant`() {
            // given
            val fixedInstant = Instant.parse("2025-01-15T05:00:00Z") // 14:00 KST
            val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
            val command = RankingCommand.FindRankings(
                period = RankingPeriod.HOURLY,
                date = null,
                page = 0,
                size = 20,
            )

            // when
            val query = command.toQuery(clock)

            // then
            assertThat(query.dateTime).isEqualTo(fixedInstant)
        }

        @DisplayName("HOURLY date 2025011514는 2025-01-15T14:00 Asia/Seoul에 해당하는 Instant로 파싱된다")
        @Test
        fun `toQuery with HOURLY date parses to correct Instant`() {
            // given
            val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
            val command = RankingCommand.FindRankings(
                period = RankingPeriod.HOURLY,
                date = "2025011514",
                page = 0,
                size = 20,
            )

            // when
            val query = command.toQuery(clock)

            // then
            assertThat(query.dateTime).isEqualTo(
                ZonedDateTime.of(2025, 1, 15, 14, 0, 0, 0, seoulZone).toInstant(),
            )
            assertThat(query.period).isEqualTo(RankingPeriod.HOURLY)
        }

        @DisplayName("DAILY date 20250115는 2025-01-15T00:00 Asia/Seoul에 해당하는 Instant로 파싱된다")
        @Test
        fun `toQuery with DAILY date parses to correct Instant`() {
            // given
            val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
            val command = RankingCommand.FindRankings(
                period = RankingPeriod.DAILY,
                date = "20250115",
                page = 0,
                size = 20,
            )

            // when
            val query = command.toQuery(clock)

            // then
            assertThat(query.dateTime).isEqualTo(
                ZonedDateTime.of(2025, 1, 15, 0, 0, 0, 0, seoulZone).toInstant(),
            )
            assertThat(query.period).isEqualTo(RankingPeriod.DAILY)
        }

        @DisplayName("offset은 page * size로 계산된다")
        @Test
        fun `toQuery correctly calculates offset from page and size`() {
            // given
            val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
            val command = RankingCommand.FindRankings(
                period = RankingPeriod.HOURLY,
                date = "2025011514",
                page = 2,
                size = 20,
            )

            // when
            val query = command.toQuery(clock)

            // then
            assertThat(query.offset).isEqualTo(40L) // 2 * 20
        }

        @DisplayName("limit은 size와 같다")
        @Test
        fun `toQuery correctly passes limit as size`() {
            // given
            val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
            val command = RankingCommand.FindRankings(
                period = RankingPeriod.HOURLY,
                date = "2025011514",
                page = 0,
                size = 15,
            )

            // when
            val query = command.toQuery(clock)

            // then
            assertThat(query.limit).isEqualTo(15L)
        }

        @DisplayName("page가 0이면 offset도 0이다")
        @Test
        fun `toQuery with page 0 has offset 0`() {
            // given
            val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
            val command = RankingCommand.FindRankings(
                period = RankingPeriod.HOURLY,
                date = "2025011514",
                page = 0,
                size = 20,
            )

            // when
            val query = command.toQuery(clock)

            // then
            assertThat(query.offset).isEqualTo(0L)
        }

        @DisplayName("WEEKLY date 20260101은 2026-01-01T00:00 Asia/Seoul에 해당하는 Instant로 파싱된다")
        @Test
        fun `toQuery with WEEKLY date parses to correct Instant`() {
            // given
            val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
            val command = RankingCommand.FindRankings(
                period = RankingPeriod.WEEKLY,
                date = "20260101",
                page = 0,
                size = 20,
            )

            // when
            val query = command.toQuery(clock)

            // then
            assertThat(query.dateTime).isEqualTo(
                ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, seoulZone).toInstant(),
            )
            assertThat(query.period).isEqualTo(RankingPeriod.WEEKLY)
        }

        @DisplayName("MONTHLY date 20260101은 2026-01-01T00:00 Asia/Seoul에 해당하는 Instant로 파싱된다")
        @Test
        fun `toQuery with MONTHLY date parses to correct Instant`() {
            // given
            val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
            val command = RankingCommand.FindRankings(
                period = RankingPeriod.MONTHLY,
                date = "20260101",
                page = 0,
                size = 20,
            )

            // when
            val query = command.toQuery(clock)

            // then
            assertThat(query.dateTime).isEqualTo(
                ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, seoulZone).toInstant(),
            )
            assertThat(query.period).isEqualTo(RankingPeriod.MONTHLY)
        }

        @DisplayName("WEEKLY date format이 yyyyMMdd가 아니면 IllegalArgumentException을 던진다")
        @Test
        fun `toQuery with invalid WEEKLY date format throws IllegalArgumentException`() {
            // given
            val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
            // 10 chars instead of 8
            val command = RankingCommand.FindRankings(
                period = RankingPeriod.WEEKLY,
                date = "2026010112",
                page = 0,
                size = 20,
            )

            // when & then
            assertThrows<IllegalArgumentException> {
                command.toQuery(clock)
            }
        }

        @DisplayName("MONTHLY date format이 yyyyMMdd가 아니면 IllegalArgumentException을 던진다")
        @Test
        fun `toQuery with invalid MONTHLY date format throws IllegalArgumentException`() {
            // given
            val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
            // 6 chars instead of 8
            val command = RankingCommand.FindRankings(
                period = RankingPeriod.MONTHLY,
                date = "202601",
                page = 0,
                size = 20,
            )

            // when & then
            assertThrows<IllegalArgumentException> {
                command.toQuery(clock)
            }
        }
    }
}
