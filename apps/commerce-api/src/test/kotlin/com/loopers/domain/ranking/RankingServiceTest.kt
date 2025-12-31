package com.loopers.domain.ranking

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RankingServiceTest {

    private val rankingWeightRepository: RankingWeightRepository = mockk()
    private val productRankingReader: ProductRankingReader = mockk()
    private val rankingService = RankingService(rankingWeightRepository, productRankingReader)

    @DisplayName("findWeight 테스트")
    @Nested
    inner class FindWeight {

        @DisplayName("저장된 가중치가 있으면 최신 가중치를 반환한다")
        @Test
        fun `returns latest weight when exists`() {
            // given
            val existingWeight = RankingWeight.create(
                viewWeight = BigDecimal("0.15"),
                likeWeight = BigDecimal("0.25"),
                orderWeight = BigDecimal("0.55"),
            )
            every { rankingWeightRepository.findLatest() } returns existingWeight

            // when
            val result = rankingService.findWeight()

            // then
            assertThat(result.viewWeight).isEqualTo(BigDecimal("0.15"))
            assertThat(result.likeWeight).isEqualTo(BigDecimal("0.25"))
            assertThat(result.orderWeight).isEqualTo(BigDecimal("0.55"))
        }

        @DisplayName("저장된 가중치가 없으면 fallback 가중치를 반환한다")
        @Test
        fun `returns fallback weight when not exists`() {
            // given
            every { rankingWeightRepository.findLatest() } returns null

            // when
            val result = rankingService.findWeight()

            // then
            assertThat(result.viewWeight).isEqualTo(BigDecimal("0.10"))
            assertThat(result.likeWeight).isEqualTo(BigDecimal("0.20"))
            assertThat(result.orderWeight).isEqualTo(BigDecimal("0.60"))
        }
    }

    @DisplayName("updateWeight 테스트")
    @Nested
    inner class UpdateWeight {

        @DisplayName("기존 가중치가 있으면 새로운 인스턴스를 생성하여 저장한다 (append-only)")
        @Test
        fun `creates new weight instance when existing weight exists (append-only)`() {
            // given
            val existingWeight = RankingWeight.create(
                viewWeight = BigDecimal("0.10"),
                likeWeight = BigDecimal("0.20"),
                orderWeight = BigDecimal("0.60"),
            )
            val savedWeightSlot = slot<RankingWeight>()
            every { rankingWeightRepository.findLatest() } returns existingWeight
            every { rankingWeightRepository.save(capture(savedWeightSlot)) } answers { firstArg() }

            val newViewWeight = BigDecimal("0.30")
            val newLikeWeight = BigDecimal("0.30")
            val newOrderWeight = BigDecimal("0.40")

            // when
            val result = rankingService.updateWeight(
                viewWeight = newViewWeight,
                likeWeight = newLikeWeight,
                orderWeight = newOrderWeight,
            )

            // then
            assertThat(result.viewWeight).isEqualTo(newViewWeight)
            assertThat(result.likeWeight).isEqualTo(newLikeWeight)
            assertThat(result.orderWeight).isEqualTo(newOrderWeight)
            assertThat(savedWeightSlot.captured).isNotSameAs(existingWeight)
        }

        @DisplayName("기존 가중치가 없으면 새로 생성하고 저장한다")
        @Test
        fun `creates new weight when not exists and saves`() {
            // given
            every { rankingWeightRepository.findLatest() } returns null
            every { rankingWeightRepository.save(any()) } answers { firstArg() }

            val viewWeight = BigDecimal("0.25")
            val likeWeight = BigDecimal("0.35")
            val orderWeight = BigDecimal("0.40")

            // when
            val result = rankingService.updateWeight(
                viewWeight = viewWeight,
                likeWeight = likeWeight,
                orderWeight = orderWeight,
            )

            // then
            assertThat(result.viewWeight).isEqualTo(viewWeight)
            assertThat(result.likeWeight).isEqualTo(likeWeight)
            assertThat(result.orderWeight).isEqualTo(orderWeight)
        }

        @DisplayName("저장소의 save 메서드가 호출된다")
        @Test
        fun `calls repository save method`() {
            // given
            val existingWeight = RankingWeight.create(
                viewWeight = BigDecimal("0.10"),
                likeWeight = BigDecimal("0.20"),
                orderWeight = BigDecimal("0.60"),
            )
            every { rankingWeightRepository.findLatest() } returns existingWeight
            every { rankingWeightRepository.save(any()) } answers { firstArg() }

            // when
            rankingService.updateWeight(
                viewWeight = BigDecimal("0.30"),
                likeWeight = BigDecimal("0.30"),
                orderWeight = BigDecimal("0.40"),
            )

            // then
            verify { rankingWeightRepository.save(any()) }
        }
    }

    @DisplayName("findRankings 테스트")
    @Nested
    inner class FindRankings {

        @DisplayName("랭킹이 있으면 그대로 반환한다")
        @Test
        fun `returns rankings when found`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                bucketKey = "ranking:products:2025011514",
                fallbackKey = "ranking:products:2025011513",
                offset = 0,
                limit = 20,
            )
            val expectedRankings = listOf(
                ProductRanking(productId = 101L, rank = 1, score = BigDecimal("100.0")),
                ProductRanking(productId = 102L, rank = 2, score = BigDecimal("90.0")),
            )
            every { productRankingReader.findTopRankings(query) } returns expectedRankings

            // when
            val result = rankingService.findRankings(query)

            // then
            assertThat(result).hasSize(2)
            assertThat(result[0].productId).isEqualTo(101L)
            assertThat(result[1].productId).isEqualTo(102L)
        }

        @DisplayName("결과가 비어있고 offset=0이고 fallbackKey가 있으면 fallback을 시도한다")
        @Test
        fun `uses fallback when empty and offset is 0 and fallbackKey exists`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                bucketKey = "ranking:products:2025011514",
                fallbackKey = "ranking:products:2025011513",
                offset = 0,
                limit = 20,
            )
            val fallbackQuery = query.copy(
                bucketKey = query.fallbackKey!!,
                fallbackKey = null,
            )
            val fallbackRankings = listOf(
                ProductRanking(productId = 201L, rank = 1, score = BigDecimal("80.0")),
            )
            every { productRankingReader.findTopRankings(query) } returns emptyList()
            every { productRankingReader.findTopRankings(fallbackQuery) } returns fallbackRankings

            // when
            val result = rankingService.findRankings(query)

            // then
            assertThat(result).hasSize(1)
            assertThat(result[0].productId).isEqualTo(201L)
            verify { productRankingReader.findTopRankings(query) }
            verify { productRankingReader.findTopRankings(fallbackQuery) }
        }

        @DisplayName("결과가 비어있어도 offset > 0이면 fallback을 시도하지 않는다")
        @Test
        fun `does not use fallback when offset is greater than 0`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                bucketKey = "ranking:products:2025011514",
                fallbackKey = "ranking:products:2025011513",
                offset = 20,
                limit = 20,
            )
            every { productRankingReader.findTopRankings(query) } returns emptyList()

            // when
            val result = rankingService.findRankings(query)

            // then
            assertThat(result).isEmpty()
            verify(exactly = 1) { productRankingReader.findTopRankings(any()) }
        }

        @DisplayName("결과가 비어있어도 fallbackKey가 null이면 fallback을 시도하지 않는다")
        @Test
        fun `does not use fallback when fallbackKey is null`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                bucketKey = "ranking:products:2025011514",
                fallbackKey = null,
                offset = 0,
                limit = 20,
            )
            every { productRankingReader.findTopRankings(query) } returns emptyList()

            // when
            val result = rankingService.findRankings(query)

            // then
            assertThat(result).isEmpty()
            verify(exactly = 1) { productRankingReader.findTopRankings(any()) }
        }

        @DisplayName("fallback도 비어있으면 빈 리스트를 반환한다")
        @Test
        fun `returns empty when fallback is also empty`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                bucketKey = "ranking:products:2025011514",
                fallbackKey = "ranking:products:2025011513",
                offset = 0,
                limit = 20,
            )
            every { productRankingReader.findTopRankings(any()) } returns emptyList()

            // when
            val result = rankingService.findRankings(query)

            // then
            assertThat(result).isEmpty()
            verify(exactly = 2) { productRankingReader.findTopRankings(any()) }
        }
    }
}
