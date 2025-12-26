package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingWeight
import com.loopers.domain.ranking.RankingWeightRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

@SpringBootTest
@DisplayName("RankingWeightRdbRepository 통합 테스트")
class RankingWeightRdbRepositoryIntegrationTest @Autowired constructor(
    private val rankingWeightRepository: RankingWeightRepository,
    private val rankingWeightJpaRepository: RankingWeightJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("findLatest()")
    @Nested
    inner class FindLatest {

        @DisplayName("레코드가 없으면 null을 반환한다")
        @Test
        fun `returns null when no record exists`() {
            // when
            val result = rankingWeightRepository.findLatest()

            // then
            assertThat(result).isNull()
        }

        @DisplayName("레코드가 존재하면 가장 최근 RankingWeight를 반환한다")
        @Test
        fun `returns the latest RankingWeight when records exist`() {
            // given
            val rankingWeight1 = RankingWeight(
                viewWeight = BigDecimal("0.10"),
                likeWeight = BigDecimal("0.20"),
                orderWeight = BigDecimal("0.60"),
            )
            val rankingWeight2 = RankingWeight(
                viewWeight = BigDecimal("0.15"),
                likeWeight = BigDecimal("0.25"),
                orderWeight = BigDecimal("0.50"),
            )
            rankingWeightJpaRepository.saveAndFlush(rankingWeight1)
            rankingWeightJpaRepository.saveAndFlush(rankingWeight2)

            // when
            val result = rankingWeightRepository.findLatest()

            // then
            assertThat(result).isNotNull
            assertThat(result!!.viewWeight).isEqualByComparingTo(BigDecimal("0.15"))
            assertThat(result.likeWeight).isEqualByComparingTo(BigDecimal("0.25"))
            assertThat(result.orderWeight).isEqualByComparingTo(BigDecimal("0.50"))
        }

        @DisplayName("삭제된 레코드는 조회되지 않는다")
        @Test
        fun `does not return soft-deleted records`() {
            // given
            val rankingWeight1 = RankingWeight(
                viewWeight = BigDecimal("0.10"),
                likeWeight = BigDecimal("0.20"),
                orderWeight = BigDecimal("0.60"),
            )
            val rankingWeight2 = RankingWeight(
                viewWeight = BigDecimal("0.15"),
                likeWeight = BigDecimal("0.25"),
                orderWeight = BigDecimal("0.50"),
            )
            rankingWeightJpaRepository.saveAndFlush(rankingWeight1)
            rankingWeightJpaRepository.saveAndFlush(rankingWeight2)

            // when - soft delete the latest record
            rankingWeight2.delete()
            rankingWeightJpaRepository.saveAndFlush(rankingWeight2)

            val result = rankingWeightRepository.findLatest()

            // then
            assertThat(result).isNotNull
            assertThat(result!!.viewWeight).isEqualByComparingTo(BigDecimal("0.10"))
            assertThat(result.likeWeight).isEqualByComparingTo(BigDecimal("0.20"))
            assertThat(result.orderWeight).isEqualByComparingTo(BigDecimal("0.60"))
        }
    }
}
