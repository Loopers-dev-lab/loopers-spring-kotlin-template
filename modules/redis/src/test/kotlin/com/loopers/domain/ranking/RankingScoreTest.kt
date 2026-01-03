package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RankingScore 단위 테스트")
class RankingScoreTest {

    @Test
    @DisplayName("점수 더하기 연산")
    fun `should add scores correctly`() {
        // given
        val score1 = RankingScore(1.5)
        val score2 = RankingScore(2.3)

        // when
        val result = score1 + score2

        // then
        assertThat(result.value).isCloseTo(3.8, org.assertj.core.data.Offset.offset(0.0001))
    }

    @Test
    @DisplayName("점수 곱하기 연산")
    fun `should multiply score correctly`() {
        // given
        val score = RankingScore(10.0)
        val multiplier = 0.1

        // when
        val result = score * multiplier

        // then
        assertThat(result.value).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001))
    }

    @Test
    @DisplayName("점수 0 생성")
    fun `should create zero score`() {
        // when
        val score = RankingScore.zero()

        // then
        assertThat(score.value).isEqualTo(0.0)
    }

    @Test
    @DisplayName("점수 합산")
    fun `should sum multiple scores`() {
        // given
        val scores = listOf(
            RankingScore(1.0),
            RankingScore(2.0),
            RankingScore(3.0),
        )

        // when
        val sum = RankingScore.sum(scores)

        // then
        assertThat(sum.value).isCloseTo(6.0, org.assertj.core.data.Offset.offset(0.0001))
    }

    @Test
    @DisplayName("음수 점수 생성 시 예외 발생")
    fun `should throw exception for negative score`() {
        assertThatThrownBy {
            RankingScore(-1.0)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("랭킹 점수는 0 이상이어야 합니다")
    }

    @Test
    @DisplayName("음수 배수로 곱하기 시 예외 발생")
    fun `should throw exception for negative multiplier`() {
        // given
        val score = RankingScore(10.0)

        // when & then
        assertThatThrownBy {
            score * -0.5
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("배수는 0 이상이어야 합니다")
    }
}
