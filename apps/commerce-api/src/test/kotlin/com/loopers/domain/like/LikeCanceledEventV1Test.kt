package com.loopers.domain.like

import com.loopers.support.event.DomainEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.Test

class LikeCanceledEventV1Test {

    @DisplayName("LikeCanceledEventV1 생성 테스트")
    @Nested
    inner class Create {

        @DisplayName("DomainEvent 인터페이스를 구현한다")
        @Test
        fun `event implements DomainEvent`() {
            // given
            val userId = 1L
            val productId = 100L

            // when
            val event = LikeCanceledEventV1(
                userId = userId,
                productId = productId,
            )

            // then
            assertThat(event).isInstanceOf(DomainEvent::class.java)
        }

        @DisplayName("eventType은 'LikeCanceledEventV1'이다")
        @Test
        fun `eventType is LikeCanceledEventV1`() {
            // given
            val userId = 1L
            val productId = 100L

            // when
            val event = LikeCanceledEventV1(
                userId = userId,
                productId = productId,
            )

            // then
            assertThat(event.eventType).isEqualTo("LikeCanceledEventV1")
        }

        @DisplayName("aggregateType은 'ProductLike'이다")
        @Test
        fun `aggregateType is ProductLike`() {
            // given
            val userId = 1L
            val productId = 100L

            // when
            val event = LikeCanceledEventV1(
                userId = userId,
                productId = productId,
            )

            // then
            assertThat(event.aggregateType).isEqualTo("ProductLike")
        }

        @DisplayName("userId와 productId가 포함된다")
        @Test
        fun `event contains userId and productId`() {
            // given
            val userId = 1L
            val productId = 100L

            // when
            val event = LikeCanceledEventV1(
                userId = userId,
                productId = productId,
            )

            // then
            assertThat(event.userId).isEqualTo(userId)
            assertThat(event.productId).isEqualTo(productId)
        }

        @DisplayName("aggregateId는 productId를 문자열로 변환한 값이다")
        @Test
        fun `aggregateId is productId as string`() {
            // given
            val userId = 1L
            val productId = 123L

            // when
            val event = LikeCanceledEventV1(
                userId = userId,
                productId = productId,
            )

            // then
            assertThat(event.aggregateId).isEqualTo("123")
        }

        @DisplayName("version은 1이다")
        @Test
        fun `version is 1`() {
            // given
            val userId = 1L
            val productId = 100L

            // when
            val event = LikeCanceledEventV1(
                userId = userId,
                productId = productId,
            )

            // then
            assertThat(event.version).isEqualTo(1)
        }
    }
}
