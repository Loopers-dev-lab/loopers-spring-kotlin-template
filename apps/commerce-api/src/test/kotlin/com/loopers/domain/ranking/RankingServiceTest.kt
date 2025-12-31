package com.loopers.domain.ranking

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal

class RankingServiceTest {

    private val rankingWeightRepository: RankingWeightRepository = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    private val rankingService = RankingService(rankingWeightRepository, eventPublisher)

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

        @DisplayName("기존 가중치가 있을 때 RankingWeightChangedEventV1 이벤트를 발행한다")
        @Test
        fun `publishes RankingWeightChangedEventV1 when existing weight exists`() {
            // given
            val existingWeight = RankingWeight.create(
                viewWeight = BigDecimal("0.10"),
                likeWeight = BigDecimal("0.20"),
                orderWeight = BigDecimal("0.60"),
            )
            every { rankingWeightRepository.findLatest() } returns existingWeight
            every { rankingWeightRepository.save(any()) } answers { firstArg() }

            val eventSlot = slot<RankingWeightChangedEventV1>()

            // when
            rankingService.updateWeight(
                viewWeight = BigDecimal("0.30"),
                likeWeight = BigDecimal("0.30"),
                orderWeight = BigDecimal("0.40"),
            )

            // then
            verify { eventPublisher.publishEvent(capture(eventSlot)) }
            assertThat(eventSlot.captured).isInstanceOf(RankingWeightChangedEventV1::class.java)
        }

        @DisplayName("기존 가중치가 없을 때도 RankingWeightChangedEventV1 이벤트를 발행한다")
        @Test
        fun `publishes RankingWeightChangedEventV1 when no existing weight`() {
            // given
            every { rankingWeightRepository.findLatest() } returns null
            every { rankingWeightRepository.save(any()) } answers { firstArg() }

            val eventSlot = slot<RankingWeightChangedEventV1>()

            // when
            rankingService.updateWeight(
                viewWeight = BigDecimal("0.25"),
                likeWeight = BigDecimal("0.35"),
                orderWeight = BigDecimal("0.40"),
            )

            // then
            verify { eventPublisher.publishEvent(capture(eventSlot)) }
            assertThat(eventSlot.captured).isInstanceOf(RankingWeightChangedEventV1::class.java)
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
}
