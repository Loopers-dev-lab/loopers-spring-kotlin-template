package com.loopers.support.idempotency

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("EventHandled 단위 테스트")
class EventHandledTest {

    @DisplayName("생성자")
    @Nested
    inner class Constructor {

        @DisplayName("idempotencyKey로 EventHandled를 생성한다")
        @Test
        fun `creates EventHandled with idempotencyKey`() {
            // given
            val idempotencyKey = "product-statistic:Order:123:paid"

            // when
            val eventHandled = EventHandled(idempotencyKey = idempotencyKey)

            // then
            assertThat(eventHandled.idempotencyKey).isEqualTo(idempotencyKey)
        }

        @DisplayName("다양한 형식의 idempotencyKey로 생성할 수 있다")
        @Test
        fun `creates EventHandled with various idempotencyKey formats`() {
            // given
            val idempotencyKey1 = "product-statistic:Order:123:paid"
            val idempotencyKey2 = "product-statistic:event-uuid-abc-123"

            // when
            val eventHandled1 = EventHandled(idempotencyKey = idempotencyKey1)
            val eventHandled2 = EventHandled(idempotencyKey = idempotencyKey2)

            // then
            assertThat(eventHandled1.idempotencyKey).isEqualTo(idempotencyKey1)
            assertThat(eventHandled2.idempotencyKey).isEqualTo(idempotencyKey2)
        }
    }

    @DisplayName("엔티티 필드 매핑")
    @Nested
    inner class FieldMapping {

        @DisplayName("영속화 전 id는 0이다")
        @Test
        fun `id is 0 before persistence`() {
            // given
            val idempotencyKey = "product-statistic:Order:123:paid"

            // when
            val eventHandled = EventHandled(idempotencyKey = idempotencyKey)

            // then
            assertThat(eventHandled.id).isEqualTo(0L)
        }

        @DisplayName("handledAt은 자동으로 설정된다 (null이 아님)")
        @Test
        fun `handledAt is set automatically and not null`() {
            // given
            val idempotencyKey = "product-statistic:Order:123:paid"

            // when
            val eventHandled = EventHandled(idempotencyKey = idempotencyKey)

            // then
            assertThat(eventHandled.handledAt).isNotNull()
        }

        @DisplayName("모든 필드가 올바르게 매핑된다")
        @Test
        fun `all fields are correctly mapped`() {
            // given
            val idempotencyKey = "product-statistic:Coupon:coupon-999:issue"

            // when
            val eventHandled = EventHandled(idempotencyKey = idempotencyKey)

            // then
            assertThat(eventHandled.id).isEqualTo(0L)
            assertThat(eventHandled.idempotencyKey).isEqualTo(idempotencyKey)
            assertThat(eventHandled.handledAt).isNotNull()
        }
    }
}
