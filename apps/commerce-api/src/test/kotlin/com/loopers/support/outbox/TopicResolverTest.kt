package com.loopers.support.outbox

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("TopicResolver 단위 테스트")
class TopicResolverTest {

    @DisplayName("eventType에서 topic 도출")
    @Nested
    inner class TopicDerivation {

        @DisplayName("'loopers.order.created.v1'은 'order-events'를 반환한다")
        @Test
        fun `order event type returns order-events topic`() {
            // given
            val eventType = "loopers.order.created.v1"

            // when
            val topic = TopicResolver.resolve(eventType)

            // then
            assertThat(topic).isEqualTo("order-events")
        }

        @DisplayName("'loopers.payment.paid.v1'은 'payment-events'를 반환한다")
        @Test
        fun `payment event type returns payment-events topic`() {
            // given
            val eventType = "loopers.payment.paid.v1"

            // when
            val topic = TopicResolver.resolve(eventType)

            // then
            assertThat(topic).isEqualTo("payment-events")
        }

        @DisplayName("'loopers.like.created.v1'은 'like-events'를 반환한다")
        @Test
        fun `like event type returns like-events topic`() {
            // given
            val eventType = "loopers.like.created.v1"

            // when
            val topic = TopicResolver.resolve(eventType)

            // then
            assertThat(topic).isEqualTo("like-events")
        }
    }

    @DisplayName("예외 케이스")
    @Nested
    inner class ExceptionCases {

        @DisplayName("잘못된 형식의 eventType은 IllegalArgumentException을 던진다")
        @Test
        fun `invalid format throws IllegalArgumentException`() {
            // given
            val invalidEventType = "invalid-format"

            // when & then
            assertThatThrownBy { TopicResolver.resolve(invalidEventType) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid event type format")
        }

        @DisplayName("점이 없는 형식은 IllegalArgumentException을 던진다")
        @Test
        fun `no dot format throws IllegalArgumentException`() {
            // given
            val invalidEventType = "loopers"

            // when & then
            assertThatThrownBy { TopicResolver.resolve(invalidEventType) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid event type format")
        }

        @DisplayName("빈 문자열은 IllegalArgumentException을 던진다")
        @Test
        fun `empty string throws IllegalArgumentException`() {
            // given
            val emptyEventType = ""

            // when & then
            assertThatThrownBy { TopicResolver.resolve(emptyEventType) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid event type format")
        }
    }
}
