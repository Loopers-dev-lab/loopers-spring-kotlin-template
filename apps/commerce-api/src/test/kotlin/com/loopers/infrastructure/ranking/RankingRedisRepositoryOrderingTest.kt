package com.loopers.infrastructure.ranking

import com.loopers.IntegrationTest
import com.loopers.domain.ranking.RankingRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate

@DisplayName("RankingRedisRepository 정렬/랭크 테스트")
class RankingRedisRepositoryOrderingTest : IntegrationTest() {

    @Autowired
    private lateinit var rankingRepository: RankingRepository

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @Test
    fun testOrderingWhenScoresTiedAcrossPageBoundaryThenProductIdAsc() {
        // given
        val dateKey = "20250102"
        val key = "ranking:all:$dateKey"
        val score = 10.0

        listOf(1L, 2L, 3L).forEach { productId ->
            redisTemplate.opsForZSet().add(key, productId.toString(), score)
        }

        // when
        val firstPage = rankingRepository.getScores(dateKey, 0, 1)
        val secondPage = rankingRepository.getScores(dateKey, 2, 2)

        // then
        assertThat(firstPage.map { it.productId }).containsExactly(3L, 2L)
        assertThat(secondPage.map { it.productId }).containsExactly(1L)
    }

    @Test
    fun testReverseRankWhenScoresTiedThenProductIdAsc() {
        // given
        val dateKey = "20250103"
        val key = "ranking:all:$dateKey"
        val score = 5.0

        listOf(1L, 2L, 3L).forEach { productId ->
            redisTemplate.opsForZSet().add(key, productId.toString(), score)
        }

        // when
        val rank1 = rankingRepository.getRank(dateKey, 1L)
        val rank2 = rankingRepository.getRank(dateKey, 2L)
        val rank3 = rankingRepository.getRank(dateKey, 3L)

        // then
        assertThat(rank3).isEqualTo(0L)
        assertThat(rank2).isEqualTo(1L)
        assertThat(rank1).isEqualTo(2L)
    }
}
