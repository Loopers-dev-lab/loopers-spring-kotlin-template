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
    private val rankingService = RankingService(
        rankingWeightRepository,
        productRankingReader,
    )

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
            val command = RankingCommand.FindRankings(
                period = RankingPeriod.HOURLY,
                date = null,
                page = 0,
                size = 20,
            )
            val expectedRankings = listOf(
                ProductRanking(productId = 101L, rank = 1, score = BigDecimal("100.0")),
                ProductRanking(productId = 102L, rank = 2, score = BigDecimal("90.0")),
            )
            every { productRankingReader.findTopRankings(any()) } returns expectedRankings

            // when
            val result = rankingService.findRankings(command)

            // then
            assertThat(result).hasSize(2)
            assertThat(result[0].productId).isEqualTo(101L)
            assertThat(result[1].productId).isEqualTo(102L)
        }

        @DisplayName("date가 지정되면 해당 date의 dateTime으로 Query를 생성한다")
        @Test
        fun `uses specified date for query dateTime`() {
            // given
            val command = RankingCommand.FindRankings(
                period = RankingPeriod.HOURLY,
                date = "2025011514",
                page = 0,
                size = 20,
            )
            val expectedRankings = listOf(
                ProductRanking(productId = 101L, rank = 1, score = BigDecimal("100.0")),
            )
            val querySlot = slot<RankingQuery>()
            every { productRankingReader.findTopRankings(capture(querySlot)) } returns expectedRankings

            // when
            val result = rankingService.findRankings(command)

            // then
            assertThat(result).hasSize(1)
            val capturedQuery = querySlot.captured
            assertThat(capturedQuery.period).isEqualTo(RankingPeriod.HOURLY)
            assertThat(capturedQuery.dateTime.year).isEqualTo(2025)
            assertThat(capturedQuery.dateTime.monthValue).isEqualTo(1)
            assertThat(capturedQuery.dateTime.dayOfMonth).isEqualTo(15)
            assertThat(capturedQuery.dateTime.hour).isEqualTo(14)
        }

        @DisplayName("ProductRankingReader가 호출된다")
        @Test
        fun `calls productRankingReader findTopRankings`() {
            // given
            val command = RankingCommand.FindRankings(
                period = RankingPeriod.HOURLY,
                date = null,
                page = 0,
                size = 20,
            )
            every { productRankingReader.findTopRankings(any()) } returns emptyList()

            // when
            rankingService.findRankings(command)

            // then
            verify(exactly = 1) { productRankingReader.findTopRankings(any()) }
        }

        @DisplayName("페이지네이션 정보가 Query에 올바르게 전달된다")
        @Test
        fun `pagination info is correctly passed to query`() {
            // given
            val command = RankingCommand.FindRankings(
                period = RankingPeriod.DAILY,
                date = "20250115",
                page = 2,
                size = 10,
            )
            val querySlot = slot<RankingQuery>()
            every { productRankingReader.findTopRankings(capture(querySlot)) } returns emptyList()

            // when
            rankingService.findRankings(command)

            // then
            val capturedQuery = querySlot.captured
            assertThat(capturedQuery.offset).isEqualTo(20L) // page 2 * size 10
            assertThat(capturedQuery.limit).isEqualTo(10L)
        }
    }
}
