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
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("save()")
    @Nested
    inner class Save {

        @DisplayName("RankingWeight를 저장하고 ID가 생성된 엔티티를 반환한다")
        @Test
        fun `persists RankingWeight and returns entity with generated id`() {
            // given
            val rankingWeight = RankingWeight.create(
                viewWeight = BigDecimal("0.15"),
                likeWeight = BigDecimal("0.25"),
                orderWeight = BigDecimal("0.55"),
            )

            // when
            val saved = rankingWeightRepository.save(rankingWeight)

            // then
            assertThat(saved.id).isGreaterThan(0L)
            assertThat(saved.viewWeight).isEqualByComparingTo(BigDecimal("0.15"))
            assertThat(saved.likeWeight).isEqualByComparingTo(BigDecimal("0.25"))
            assertThat(saved.orderWeight).isEqualByComparingTo(BigDecimal("0.55"))
            assertThat(saved.createdAt).isNotNull()
            assertThat(saved.updatedAt).isNotNull()
            assertThat(saved.deletedAt).isNull()
        }
    }

    @DisplayName("findLatest()")
    @Nested
    inner class FindLatest {

        @DisplayName("가장 최근에 저장된 RankingWeight를 반환한다")
        @Test
        fun `returns the most recently saved RankingWeight`() {
            // given
            val first = rankingWeightRepository.save(
                RankingWeight.create(
                    viewWeight = BigDecimal("0.10"),
                    likeWeight = BigDecimal("0.20"),
                    orderWeight = BigDecimal("0.60"),
                ),
            )
            val second = rankingWeightRepository.save(
                RankingWeight.create(
                    viewWeight = BigDecimal("0.15"),
                    likeWeight = BigDecimal("0.25"),
                    orderWeight = BigDecimal("0.55"),
                ),
            )
            val third = rankingWeightRepository.save(
                RankingWeight.create(
                    viewWeight = BigDecimal("0.20"),
                    likeWeight = BigDecimal("0.30"),
                    orderWeight = BigDecimal("0.50"),
                ),
            )

            // when
            val latest = rankingWeightRepository.findLatest()

            // then
            assertThat(latest).isNotNull
            assertThat(latest!!.id).isEqualTo(third.id)
            assertThat(latest.viewWeight).isEqualByComparingTo(BigDecimal("0.20"))
            assertThat(latest.likeWeight).isEqualByComparingTo(BigDecimal("0.30"))
            assertThat(latest.orderWeight).isEqualByComparingTo(BigDecimal("0.50"))
        }

        @DisplayName("저장된 RankingWeight가 없으면 null을 반환한다")
        @Test
        fun `returns null when no RankingWeight exists`() {
            // when
            val latest = rankingWeightRepository.findLatest()

            // then
            assertThat(latest).isNull()
        }

        @DisplayName("삭제된 RankingWeight는 반환하지 않는다")
        @Test
        fun `does not return deleted RankingWeight`() {
            // given
            val active = rankingWeightRepository.save(
                RankingWeight.create(
                    viewWeight = BigDecimal("0.10"),
                    likeWeight = BigDecimal("0.20"),
                    orderWeight = BigDecimal("0.60"),
                ),
            )
            val deleted = rankingWeightRepository.save(
                RankingWeight.create(
                    viewWeight = BigDecimal("0.15"),
                    likeWeight = BigDecimal("0.25"),
                    orderWeight = BigDecimal("0.55"),
                ),
            )
            deleted.delete()
            rankingWeightRepository.save(deleted)

            // when
            val latest = rankingWeightRepository.findLatest()

            // then
            assertThat(latest).isNotNull
            assertThat(latest!!.id).isEqualTo(active.id)
        }
    }
}
