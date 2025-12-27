package com.loopers.infrastructure.ranking

import com.loopers.IntegrationTest
import com.loopers.domain.ranking.RankingRepository
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@DisplayName("RankingRedisRepository 음수 방지 테스트")
class RankingRedisRepositoryTest : IntegrationTest() {

    @Autowired
    private lateinit var rankingRepository: RankingRepository

    @Autowired
    private lateinit var redisCleanUp: RedisCleanUp

    @AfterEach
    fun tearDownRedis() {
        redisCleanUp.truncateAll()
    }

    @Test
    @DisplayName("감소 후 점수가 음수가 되면 0으로 보정된다")
    fun testBatchDecrementWhenBelowZeroThenClampedToZero() {
        // given
        val dateKey = "20251223"
        val productId = 1L
        rankingRepository.batchIncrementScores(dateKey, mapOf(productId to 1.0))

        // when
        rankingRepository.batchDecrementScores(dateKey, mapOf(productId to 5.0))

        // then
        assertThat(rankingRepository.getScore(dateKey, productId)).isEqualTo(0.0)
    }

    @Test
    @DisplayName("감소 후 점수가 0 이상이면 그대로 반영된다")
    fun testBatchDecrementWhenPositiveThenApplied() {
        // given
        val dateKey = "20251223"
        val productId = 2L
        rankingRepository.batchIncrementScores(dateKey, mapOf(productId to 10.0))

        // when
        rankingRepository.batchDecrementScores(dateKey, mapOf(productId to 3.0))

        // then
        assertThat(rankingRepository.getScore(dateKey, productId)).isCloseTo(7.0, Offset.offset(0.0001))
    }
}
