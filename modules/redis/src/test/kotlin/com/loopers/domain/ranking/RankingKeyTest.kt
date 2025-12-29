package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

@DisplayName("RankingKey 단위 테스트")
class RankingKeyTest {

    @Test
    @DisplayName("일간 랭킹 키 생성 - Redis 키 포맷 검증")
    fun `should create daily ranking key with correct format`() {
        // given
        val date = LocalDate.of(2025, 12, 20)
        val scope = RankingScope.ALL

        // when
        val key = RankingKey.daily(scope, date)

        // then
        assertThat(key.toRedisKey()).isEqualTo("ranking:all:daily:20251220")
        assertThat(key.scope).isEqualTo(RankingScope.ALL)
        assertThat(key.window).isEqualTo(TimeWindow.DAILY)
        assertThat(key.timestamp.toLocalDate()).isEqualTo(date)
    }

    @Test
    @DisplayName("시간별 랭킹 키 생성 - Redis 키 포맷 검증")
    fun `should create hourly ranking key with correct format`() {
        // given
        val dateTime = LocalDateTime.of(2025, 12, 20, 14, 30, 45)
        val scope = RankingScope.ALL

        // when
        val key = RankingKey.hourly(scope, dateTime)

        // then
        assertThat(key.toRedisKey()).isEqualTo("ranking:all:hourly:2025122014")
        assertThat(key.scope).isEqualTo(RankingScope.ALL)
        assertThat(key.window).isEqualTo(TimeWindow.HOURLY)
        // 시간별 랭킹은 분/초/나노초가 0으로 정규화됨
        assertThat(key.timestamp).isEqualTo(LocalDateTime.of(2025, 12, 20, 14, 0, 0))
    }

    @Test
    @DisplayName("현재 일간 랭킹 키 생성")
    fun `should create current daily ranking key`() {
        // given
        val today = LocalDate.now()

        // when
        val key = RankingKey.currentDaily(RankingScope.ALL)

        // then
        assertThat(key.scope).isEqualTo(RankingScope.ALL)
        assertThat(key.window).isEqualTo(TimeWindow.DAILY)
        assertThat(key.timestamp.toLocalDate()).isEqualTo(today)
    }

    @Test
    @DisplayName("현재 시간별 랭킹 키 생성")
    fun `should create current hourly ranking key`() {
        // given
        val now = LocalDateTime.now()
        val currentHour = now.withMinute(0).withSecond(0).withNano(0)

        // when
        val key = RankingKey.currentHourly(RankingScope.ALL)

        // then
        assertThat(key.scope).isEqualTo(RankingScope.ALL)
        assertThat(key.window).isEqualTo(TimeWindow.HOURLY)
        assertThat(key.timestamp).isEqualTo(currentHour)
    }

    @Test
    @DisplayName("TimeWindow TTL 검증 - 일간 2일, 시간별 1일")
    fun `should have correct TTL for each time window`() {
        assertThat(TimeWindow.DAILY.ttlDays).isEqualTo(2)
        assertThat(TimeWindow.HOURLY.ttlDays).isEqualTo(1)
    }
}
