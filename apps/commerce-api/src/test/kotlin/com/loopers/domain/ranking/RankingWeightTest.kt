package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RankingWeightTest {

    @DisplayName("RankingWeight 생성 테스트")
    @Nested
    inner class Create {

        @DisplayName("유효한 가중치로 RankingWeight를 생성한다")
        @Test
        fun `create RankingWeight with valid weights`() {
            // given
            val viewWeight = BigDecimal("0.10")
            val likeWeight = BigDecimal("0.20")
            val orderWeight = BigDecimal("0.60")

            // when
            val rankingWeight = RankingWeight.create(
                viewWeight = viewWeight,
                likeWeight = likeWeight,
                orderWeight = orderWeight,
            )

            // then
            assertThat(rankingWeight.viewWeight).isEqualTo(viewWeight)
            assertThat(rankingWeight.likeWeight).isEqualTo(likeWeight)
            assertThat(rankingWeight.orderWeight).isEqualTo(orderWeight)
        }

        @DisplayName("가중치 경계값 0으로 생성할 수 있다")
        @Test
        fun `create RankingWeight with zero weights`() {
            // given
            val weight = BigDecimal.ZERO

            // when
            val rankingWeight = RankingWeight.create(
                viewWeight = weight,
                likeWeight = weight,
                orderWeight = weight,
            )

            // then
            assertThat(rankingWeight.viewWeight).isEqualTo(weight)
            assertThat(rankingWeight.likeWeight).isEqualTo(weight)
            assertThat(rankingWeight.orderWeight).isEqualTo(weight)
        }

        @DisplayName("가중치 경계값 1으로 생성할 수 있다")
        @Test
        fun `create RankingWeight with max weights`() {
            // given
            val weight = BigDecimal.ONE

            // when
            val rankingWeight = RankingWeight.create(
                viewWeight = weight,
                likeWeight = weight,
                orderWeight = weight,
            )

            // then
            assertThat(rankingWeight.viewWeight).isEqualTo(weight)
            assertThat(rankingWeight.likeWeight).isEqualTo(weight)
            assertThat(rankingWeight.orderWeight).isEqualTo(weight)
        }

        @DisplayName("viewWeight가 0보다 작으면 예외가 발생한다")
        @Test
        fun `throw exception when viewWeight is less than 0`() {
            // given
            val invalidWeight = BigDecimal("-0.01")

            // when & then
            assertThatThrownBy {
                RankingWeight.create(
                    viewWeight = invalidWeight,
                    likeWeight = BigDecimal("0.20"),
                    orderWeight = BigDecimal("0.60"),
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("viewWeight must be between 0 and 1")
        }

        @DisplayName("viewWeight가 1보다 크면 예외가 발생한다")
        @Test
        fun `throw exception when viewWeight is greater than 1`() {
            // given
            val invalidWeight = BigDecimal("1.01")

            // when & then
            assertThatThrownBy {
                RankingWeight.create(
                    viewWeight = invalidWeight,
                    likeWeight = BigDecimal("0.20"),
                    orderWeight = BigDecimal("0.60"),
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("viewWeight must be between 0 and 1")
        }

        @DisplayName("likeWeight가 범위를 벗어나면 예외가 발생한다")
        @Test
        fun `throw exception when likeWeight is out of range`() {
            // given
            val invalidWeight = BigDecimal("1.50")

            // when & then
            assertThatThrownBy {
                RankingWeight.create(
                    viewWeight = BigDecimal("0.10"),
                    likeWeight = invalidWeight,
                    orderWeight = BigDecimal("0.60"),
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("likeWeight must be between 0 and 1")
        }

        @DisplayName("orderWeight가 범위를 벗어나면 예외가 발생한다")
        @Test
        fun `throw exception when orderWeight is out of range`() {
            // given
            val invalidWeight = BigDecimal("-0.50")

            // when & then
            assertThatThrownBy {
                RankingWeight.create(
                    viewWeight = BigDecimal("0.10"),
                    likeWeight = BigDecimal("0.20"),
                    orderWeight = invalidWeight,
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("orderWeight must be between 0 and 1")
        }
    }

    @DisplayName("RankingWeight createNext 테스트")
    @Nested
    inner class CreateNext {

        @DisplayName("createNext로 새로운 인스턴스가 생성된다")
        @Test
        fun `createNext creates a new instance`() {
            // given
            val original = createRankingWeight()
            val newViewWeight = BigDecimal("0.30")
            val newLikeWeight = BigDecimal("0.30")
            val newOrderWeight = BigDecimal("0.40")

            // when
            val next = original.createNext(
                viewWeight = newViewWeight,
                likeWeight = newLikeWeight,
                orderWeight = newOrderWeight,
            )

            // then
            assertThat(next.viewWeight).isEqualTo(newViewWeight)
            assertThat(next.likeWeight).isEqualTo(newLikeWeight)
            assertThat(next.orderWeight).isEqualTo(newOrderWeight)
            assertThat(next).isNotSameAs(original)
        }

        @DisplayName("createNext 후 원본 인스턴스는 변경되지 않는다")
        @Test
        fun `original instance remains unchanged after createNext`() {
            // given
            val originalViewWeight = BigDecimal("0.10")
            val originalLikeWeight = BigDecimal("0.20")
            val originalOrderWeight = BigDecimal("0.60")
            val original = createRankingWeight(
                viewWeight = originalViewWeight,
                likeWeight = originalLikeWeight,
                orderWeight = originalOrderWeight,
            )

            // when
            original.createNext(
                viewWeight = BigDecimal("0.30"),
                likeWeight = BigDecimal("0.30"),
                orderWeight = BigDecimal("0.40"),
            )

            // then
            assertThat(original.viewWeight).isEqualTo(originalViewWeight)
            assertThat(original.likeWeight).isEqualTo(originalLikeWeight)
            assertThat(original.orderWeight).isEqualTo(originalOrderWeight)
        }

        @DisplayName("createNext 시 잘못된 가중치면 예외가 발생한다")
        @Test
        fun `throw exception when createNext with invalid weights`() {
            // given
            val original = createRankingWeight()
            val invalidWeight = BigDecimal("1.50")

            // when & then
            assertThatThrownBy {
                original.createNext(
                    viewWeight = invalidWeight,
                    likeWeight = BigDecimal("0.30"),
                    orderWeight = BigDecimal("0.40"),
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("viewWeight must be between 0 and 1")
        }

        @DisplayName("createNext 시 RankingWeightChangedEventV1이 새 인스턴스에 등록된다")
        @Test
        fun `registers RankingWeightChangedEventV1 on new instance when createNext`() {
            // given
            val original = createRankingWeight()

            // when
            val next = original.createNext(
                viewWeight = BigDecimal("0.30"),
                likeWeight = BigDecimal("0.30"),
                orderWeight = BigDecimal("0.40"),
            )
            val events = next.pollEvents()

            // then
            assertThat(events).hasSize(1)
            assertThat(events[0]).isInstanceOf(RankingWeightChangedEventV1::class.java)
        }

        @DisplayName("createNext 시 원본 인스턴스에는 이벤트가 등록되지 않는다")
        @Test
        fun `original instance has no events after createNext`() {
            // given
            val original = createRankingWeight()

            // when
            original.createNext(
                viewWeight = BigDecimal("0.30"),
                likeWeight = BigDecimal("0.30"),
                orderWeight = BigDecimal("0.40"),
            )
            val events = original.pollEvents()

            // then
            assertThat(events).isEmpty()
        }

        @DisplayName("pollEvents 호출 후 이벤트 목록이 비워진다")
        @Test
        fun `clears events after pollEvents is called`() {
            // given
            val original = createRankingWeight()
            val next = original.createNext(
                viewWeight = BigDecimal("0.30"),
                likeWeight = BigDecimal("0.30"),
                orderWeight = BigDecimal("0.40"),
            )

            // when
            next.pollEvents()
            val eventsAfterPoll = next.pollEvents()

            // then
            assertThat(eventsAfterPoll).isEmpty()
        }
    }

    @DisplayName("fallback 팩토리 메서드 테스트")
    @Nested
    inner class Fallback {

        @DisplayName("fallback으로 기본 가중치 RankingWeight를 생성한다")
        @Test
        fun `create default RankingWeight with fallback`() {
            // when
            val rankingWeight = RankingWeight.fallback()

            // then
            assertThat(rankingWeight.viewWeight).isEqualTo(BigDecimal("0.10"))
            assertThat(rankingWeight.likeWeight).isEqualTo(BigDecimal("0.20"))
            assertThat(rankingWeight.orderWeight).isEqualTo(BigDecimal("0.60"))
        }
    }

    private fun createRankingWeight(
        viewWeight: BigDecimal = BigDecimal("0.10"),
        likeWeight: BigDecimal = BigDecimal("0.20"),
        orderWeight: BigDecimal = BigDecimal("0.60"),
    ): RankingWeight {
        return RankingWeight.create(
            viewWeight = viewWeight,
            likeWeight = likeWeight,
            orderWeight = orderWeight,
        )
    }
}
