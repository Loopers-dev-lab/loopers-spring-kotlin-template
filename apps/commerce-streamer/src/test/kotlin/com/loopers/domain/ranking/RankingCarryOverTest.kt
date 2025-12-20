package com.loopers.domain.ranking

import com.loopers.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@DisplayName("Ranking carry-over 테스트")
class RankingCarryOverTest : IntegrationTest() {

    @Autowired
    private lateinit var rankingRepository: RankingRepository

    @Test
    @DisplayName("이월 시 기존 점수는 유지되고 10%가 추가된다")
    fun testCarryOverWhenTargetHasExistingScoresThenAddsWeightedScores() {
        // given
        val sourceDateKey = "20251221"
        val targetDateKey = "20251222"

        rankingRepository.batchIncrementScores(
            sourceDateKey,
            mapOf(
                1L to 10.0,
                2L to 20.0,
            ),
        )
        rankingRepository.batchIncrementScores(
            targetDateKey,
            mapOf(
                1L to 5.0,
                3L to 7.0,
            ),
        )

        // when
        rankingRepository.carryOverScores(sourceDateKey, targetDateKey, 0.1)

        // then
        assertThat(rankingRepository.getScore(targetDateKey, 1L)).isCloseTo(6.0, Offset.offset(0.0001))
        assertThat(rankingRepository.getScore(targetDateKey, 2L)).isCloseTo(2.0, Offset.offset(0.0001))
        assertThat(rankingRepository.getScore(targetDateKey, 3L)).isCloseTo(7.0, Offset.offset(0.0001))
    }
}
