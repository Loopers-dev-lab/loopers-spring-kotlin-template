package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingPeriod
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate

@DisplayName("RankingCacheKeys")
class RankingCacheKeysTest {

    @Nested
    @DisplayName("RankingList")
    inner class RankingListTest {

        @Test
        @DisplayName("key는 ranking-cache:v1:{period}:{baseDate}:{offset}:{limit} 형식이어야 한다")
        fun `key should follow expected format`() {
            // given
            val period = RankingPeriod.WEEKLY
            val baseDate = LocalDate.of(2026, 1, 1)
            val offset = 0L
            val limit = 10

            // when
            val cacheKey = RankingCacheKeys.RankingList(
                period = period,
                baseDate = baseDate,
                offset = offset,
                limit = limit,
            )

            // then
            assertThat(cacheKey.key).isEqualTo("ranking-cache:v1:weekly:2026-01-01:0:10")
        }

        @Test
        @DisplayName("traceKey는 ranking-cache이어야 한다")
        fun `traceKey should be ranking-cache`() {
            // given
            val cacheKey = RankingCacheKeys.RankingList(
                period = RankingPeriod.MONTHLY,
                baseDate = LocalDate.of(2026, 1, 15),
                offset = 10L,
                limit = 20,
            )

            // then
            assertThat(cacheKey.traceKey).isEqualTo("ranking-cache")
        }

        @Test
        @DisplayName("TTL은 1시간이어야 한다")
        fun `ttl should be 1 hour`() {
            // given
            val cacheKey = RankingCacheKeys.RankingList(
                period = RankingPeriod.WEEKLY,
                baseDate = LocalDate.of(2026, 1, 1),
                offset = 0L,
                limit = 10,
            )

            // then
            assertThat(cacheKey.ttl).isEqualTo(Duration.ofHours(1))
        }

        @Nested
        @DisplayName("shouldCache")
        inner class ShouldCacheTest {

            @Test
            @DisplayName("WEEKLY period면 true를 반환해야 한다")
            fun `should return true for WEEKLY`() {
                // given
                val cacheKey = RankingCacheKeys.RankingList(
                    period = RankingPeriod.WEEKLY,
                    baseDate = LocalDate.of(2026, 1, 1),
                    offset = 0L,
                    limit = 10,
                )

                // when & then
                assertThat(cacheKey.shouldCache()).isTrue()
            }

            @Test
            @DisplayName("MONTHLY period면 true를 반환해야 한다")
            fun `should return true for MONTHLY`() {
                // given
                val cacheKey = RankingCacheKeys.RankingList(
                    period = RankingPeriod.MONTHLY,
                    baseDate = LocalDate.of(2026, 1, 1),
                    offset = 0L,
                    limit = 10,
                )

                // when & then
                assertThat(cacheKey.shouldCache()).isTrue()
            }

            @Test
            @DisplayName("HOURLY period면 false를 반환해야 한다")
            fun `should return false for HOURLY`() {
                // given
                val cacheKey = RankingCacheKeys.RankingList(
                    period = RankingPeriod.HOURLY,
                    baseDate = LocalDate.of(2026, 1, 1),
                    offset = 0L,
                    limit = 10,
                )

                // when & then
                assertThat(cacheKey.shouldCache()).isFalse()
            }

            @Test
            @DisplayName("DAILY period면 false를 반환해야 한다")
            fun `should return false for DAILY`() {
                // given
                val cacheKey = RankingCacheKeys.RankingList(
                    period = RankingPeriod.DAILY,
                    baseDate = LocalDate.of(2026, 1, 1),
                    offset = 0L,
                    limit = 10,
                )

                // when & then
                assertThat(cacheKey.shouldCache()).isFalse()
            }
        }

        @Test
        @DisplayName("다른 offset과 limit으로 다른 key가 생성되어야 한다")
        fun `different offset and limit should produce different keys`() {
            // given
            val baseParams = mapOf(
                "period" to RankingPeriod.WEEKLY,
                "baseDate" to LocalDate.of(2026, 1, 1),
            )

            val cacheKey1 = RankingCacheKeys.RankingList(
                period = baseParams["period"] as RankingPeriod,
                baseDate = baseParams["baseDate"] as LocalDate,
                offset = 0L,
                limit = 10,
            )

            val cacheKey2 = RankingCacheKeys.RankingList(
                period = baseParams["period"] as RankingPeriod,
                baseDate = baseParams["baseDate"] as LocalDate,
                offset = 10L,
                limit = 10,
            )

            // then
            assertThat(cacheKey1.key).isNotEqualTo(cacheKey2.key)
        }
    }
}
