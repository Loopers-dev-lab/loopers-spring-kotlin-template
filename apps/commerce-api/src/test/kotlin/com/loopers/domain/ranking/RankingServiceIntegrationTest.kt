package com.loopers.domain.ranking

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
@DisplayName("RankingService 통합 테스트")
class RankingServiceIntegrationTest @Autowired constructor(
    private val rankingService: RankingService,
    private val rankingWeightRepository: RankingWeightRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("findWeight()")
    @Nested
    inner class FindWeight {

        @DisplayName("저장된 가중치가 있으면 최신 가중치를 반환한다")
        @Test
        fun `returns latest weight when exists`() {
            // given
            rankingWeightRepository.save(
                RankingWeight.create(
                    viewWeight = BigDecimal("0.15"),
                    likeWeight = BigDecimal("0.25"),
                    orderWeight = BigDecimal("0.55"),
                ),
            )

            // when
            val result = rankingService.findWeight()

            // then
            assertThat(result.viewWeight).isEqualByComparingTo(BigDecimal("0.15"))
            assertThat(result.likeWeight).isEqualByComparingTo(BigDecimal("0.25"))
            assertThat(result.orderWeight).isEqualByComparingTo(BigDecimal("0.55"))
        }

        @DisplayName("저장된 가중치가 없으면 fallback 가중치를 반환한다")
        @Test
        fun `returns fallback weight when not exists`() {
            // when
            val result = rankingService.findWeight()

            // then
            assertThat(result.viewWeight).isEqualByComparingTo(BigDecimal("0.10"))
            assertThat(result.likeWeight).isEqualByComparingTo(BigDecimal("0.20"))
            assertThat(result.orderWeight).isEqualByComparingTo(BigDecimal("0.60"))
        }
    }

    @DisplayName("updateWeight()")
    @Nested
    inner class UpdateWeight {

        @DisplayName("기존 가중치가 있으면 업데이트하고 저장한다")
        @Test
        fun `updates existing weight and saves`() {
            // given
            rankingWeightRepository.save(
                RankingWeight.create(
                    viewWeight = BigDecimal("0.10"),
                    likeWeight = BigDecimal("0.20"),
                    orderWeight = BigDecimal("0.60"),
                ),
            )

            // when
            val result = rankingService.updateWeight(
                viewWeight = BigDecimal("0.30"),
                likeWeight = BigDecimal("0.30"),
                orderWeight = BigDecimal("0.40"),
            )

            // then
            assertThat(result.viewWeight).isEqualByComparingTo(BigDecimal("0.30"))
            assertThat(result.likeWeight).isEqualByComparingTo(BigDecimal("0.30"))
            assertThat(result.orderWeight).isEqualByComparingTo(BigDecimal("0.40"))

            // verify persisted
            val latest = rankingWeightRepository.findLatest()
            assertThat(latest).isNotNull
            assertThat(latest!!.viewWeight).isEqualByComparingTo(BigDecimal("0.30"))
            assertThat(latest.likeWeight).isEqualByComparingTo(BigDecimal("0.30"))
            assertThat(latest.orderWeight).isEqualByComparingTo(BigDecimal("0.40"))
        }

        @DisplayName("기존 가중치가 없으면 새로 생성하고 저장한다")
        @Test
        fun `creates new weight when not exists and saves`() {
            // when
            val result = rankingService.updateWeight(
                viewWeight = BigDecimal("0.25"),
                likeWeight = BigDecimal("0.35"),
                orderWeight = BigDecimal("0.40"),
            )

            // then
            assertThat(result.id).isGreaterThan(0L)
            assertThat(result.viewWeight).isEqualByComparingTo(BigDecimal("0.25"))
            assertThat(result.likeWeight).isEqualByComparingTo(BigDecimal("0.35"))
            assertThat(result.orderWeight).isEqualByComparingTo(BigDecimal("0.40"))

            // verify persisted
            val latest = rankingWeightRepository.findLatest()
            assertThat(latest).isNotNull
            assertThat(latest!!.viewWeight).isEqualByComparingTo(BigDecimal("0.25"))
        }
    }
}
