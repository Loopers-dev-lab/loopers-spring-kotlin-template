package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingKey
import com.loopers.domain.ranking.RankingScope
import com.loopers.domain.ranking.RankingScore
import com.loopers.testcontainers.RedisTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ContextConfiguration
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@ContextConfiguration(initializers = [RedisTestContainersConfig::class])
@DisplayName("RankingRedisRepository 통합 테스트")
class RankingRedisRepositoryIntegrationTest @Autowired constructor(
    private val rankingRepository: RankingRedisRepository,
    private val redisTemplate: RedisTemplate<String, String>,
) {
    private lateinit var dailyKey: RankingKey
    private lateinit var hourlyKey: RankingKey

    @BeforeEach
    fun setUp() {
        val date = LocalDate.of(2025, 12, 20)
        val dateTime = LocalDateTime.of(2025, 12, 20, 14, 0)

        dailyKey = RankingKey.daily(RankingScope.ALL, date)
        hourlyKey = RankingKey.hourly(RankingScope.ALL, dateTime)
    }

    @AfterEach
    fun tearDown() {
        // 테스트 데이터 정리
        redisTemplate.delete(dailyKey.toRedisKey())
        redisTemplate.delete(hourlyKey.toRedisKey())
    }

    @Test
    @DisplayName("점수 증가 - ZINCRBY")
    fun `should increment score successfully`() {
        // given
        val productId = 100L
        val score = RankingScore(1.5)

        // when
        val newScore1 = rankingRepository.incrementScore(dailyKey, productId, score)
        val newScore2 = rankingRepository.incrementScore(dailyKey, productId, score)

        // then
        assertThat(newScore1).isEqualTo(1.5)
        assertThat(newScore2).isEqualTo(3.0) // 누적됨
    }

    @Test
    @DisplayName("배치 점수 증가")
    fun `should increment scores in batch`() {
        // given
        val scoreMap = mapOf(
            100L to RankingScore(1.0),
            101L to RankingScore(2.0),
            102L to RankingScore(3.0),
        )

        // when
        rankingRepository.incrementScoreBatch(dailyKey, scoreMap)

        // then
        val score100 = rankingRepository.getScore(dailyKey, 100L)
        val score101 = rankingRepository.getScore(dailyKey, 101L)
        val score102 = rankingRepository.getScore(dailyKey, 102L)

        assertThat(score100?.value).isEqualTo(1.0)
        assertThat(score101?.value).isEqualTo(2.0)
        assertThat(score102?.value).isEqualTo(3.0)
    }

    @Test
    @DisplayName("Top-N 조회 - ZREVRANGE")
    fun `should get top N rankings`() {
        // given
        rankingRepository.incrementScore(dailyKey, 100L, RankingScore(10.0))
        rankingRepository.incrementScore(dailyKey, 101L, RankingScore(8.5))
        rankingRepository.incrementScore(dailyKey, 102L, RankingScore(7.2))
        rankingRepository.incrementScore(dailyKey, 103L, RankingScore(6.0))

        // when
        val top3 = rankingRepository.getTopN(dailyKey, 0, 2)

        // then
        assertThat(top3).hasSize(3)
        assertThat(top3[0].productId).isEqualTo(100L)
        assertThat(top3[0].rank).isEqualTo(1)
        assertThat(top3[0].score.value).isEqualTo(10.0)

        assertThat(top3[1].productId).isEqualTo(101L)
        assertThat(top3[1].rank).isEqualTo(2)

        assertThat(top3[2].productId).isEqualTo(102L)
        assertThat(top3[2].rank).isEqualTo(3)
    }

    @Test
    @DisplayName("특정 상품 순위 조회 - ZREVRANK")
    fun `should get product rank`() {
        // given
        rankingRepository.incrementScore(dailyKey, 100L, RankingScore(10.0))
        rankingRepository.incrementScore(dailyKey, 101L, RankingScore(8.5))
        rankingRepository.incrementScore(dailyKey, 102L, RankingScore(7.2))

        // when
        val rank100 = rankingRepository.getRank(dailyKey, 100L)
        val rank101 = rankingRepository.getRank(dailyKey, 101L)
        val rank102 = rankingRepository.getRank(dailyKey, 102L)
        val rankNotExist = rankingRepository.getRank(dailyKey, 999L)

        // then
        assertThat(rank100).isEqualTo(1)
        assertThat(rank101).isEqualTo(2)
        assertThat(rank102).isEqualTo(3)
        assertThat(rankNotExist).isNull()
    }

    @Test
    @DisplayName("특정 상품 점수 조회 - ZSCORE")
    fun `should get product score`() {
        // given
        rankingRepository.incrementScore(dailyKey, 100L, RankingScore(10.5))

        // when
        val score = rankingRepository.getScore(dailyKey, 100L)
        val scoreNotExist = rankingRepository.getScore(dailyKey, 999L)

        // then
        assertThat(score).isNotNull
        assertThat(score!!.value).isEqualTo(10.5)
        assertThat(scoreNotExist).isNull()
    }

    @Test
    @DisplayName("랭킹 항목 수 조회 - ZCARD")
    fun `should get count of ranking items`() {
        // given
        rankingRepository.incrementScore(dailyKey, 100L, RankingScore(10.0))
        rankingRepository.incrementScore(dailyKey, 101L, RankingScore(8.5))
        rankingRepository.incrementScore(dailyKey, 102L, RankingScore(7.2))

        // when
        val count = rankingRepository.getCount(dailyKey)

        // then
        assertThat(count).isEqualTo(3)
    }

    @Test
    @DisplayName("TTL 설정")
    fun `should set TTL for ranking key`() {
        // given
        rankingRepository.incrementScore(dailyKey, 100L, RankingScore(10.0))

        // when
        rankingRepository.setExpire(dailyKey)

        // then
        val ttl = redisTemplate.getExpire(dailyKey.toRedisKey())
        assertThat(ttl).isGreaterThan(0L) // TTL이 설정되었음
    }

    @Test
    @DisplayName("가중치를 곱해서 랭킹 복사 - 콜드 스타트 방지")
    fun `should copy ranking with weight for cold start prevention`() {
        // given
        val today = LocalDate.of(2025, 12, 20)
        val tomorrow = today.plusDays(1)

        val todayKey = RankingKey.daily(RankingScope.ALL, today)
        val tomorrowKey = RankingKey.daily(RankingScope.ALL, tomorrow)

        // 오늘 랭킹 데이터 생성
        rankingRepository.incrementScore(todayKey, 100L, RankingScore(10.0))
        rankingRepository.incrementScore(todayKey, 101L, RankingScore(8.5))
        rankingRepository.incrementScore(todayKey, 102L, RankingScore(7.2))

        // when
        // 10% 가중치로 내일 랭킹에 복사
        rankingRepository.copyWithWeight(todayKey, tomorrowKey, 0.1)

        // then
        // 내일 랭킹에 10% 점수로 복사되었는지 확인
        val score100 = rankingRepository.getScore(tomorrowKey, 100L)
        val score101 = rankingRepository.getScore(tomorrowKey, 101L)
        val score102 = rankingRepository.getScore(tomorrowKey, 102L)

        assertThat(score100?.value).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001))
        assertThat(score101?.value).isCloseTo(0.85, org.assertj.core.data.Offset.offset(0.001))
        assertThat(score102?.value).isCloseTo(0.72, org.assertj.core.data.Offset.offset(0.001))

        // 순위는 유지되어야 함
        val rank100 = rankingRepository.getRank(tomorrowKey, 100L)
        val rank101 = rankingRepository.getRank(tomorrowKey, 101L)
        val rank102 = rankingRepository.getRank(tomorrowKey, 102L)

        assertThat(rank100).isEqualTo(1)
        assertThat(rank101).isEqualTo(2)
        assertThat(rank102).isEqualTo(3)

        // cleanup
        redisTemplate.delete(todayKey.toRedisKey())
        redisTemplate.delete(tomorrowKey.toRedisKey())
    }

    @Test
    @DisplayName("빈 맵으로 배치 증가 - 아무 작업 안 함")
    fun `should do nothing for empty score map`() {
        // given
        val emptyMap = emptyMap<Long, RankingScore>()

        // when
        rankingRepository.incrementScoreBatch(dailyKey, emptyMap)

        // then
        val count = rankingRepository.getCount(dailyKey)
        assertThat(count).isEqualTo(0)
    }
}
