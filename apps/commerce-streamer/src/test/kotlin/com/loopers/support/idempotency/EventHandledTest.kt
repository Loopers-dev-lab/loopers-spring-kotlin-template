package com.loopers.support.idempotency

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("EventHandled 단위 테스트")
class EventHandledTest {

    @DisplayName("create() 팩토리 메서드")
    @Nested
    inner class CreateFactory {

        @DisplayName("주어진 파라미터로 EventHandled를 생성한다")
        @Test
        fun `creates EventHandled with given parameters`() {
            // given
            val aggregateType = "Order"
            val aggregateId = "123"
            val action = "deductPoint"

            // when
            val eventHandled = EventHandled.create(
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                action = action,
            )

            // then
            assertThat(eventHandled.aggregateType).isEqualTo(aggregateType)
            assertThat(eventHandled.aggregateId).isEqualTo(aggregateId)
            assertThat(eventHandled.action).isEqualTo(action)
        }

        @DisplayName("다른 액션 타입의 EventHandled를 생성한다")
        @Test
        fun `creates EventHandled with different action type`() {
            // given
            val aggregateType = "Payment"
            val aggregateId = "789"
            val action = "updateStock"

            // when
            val eventHandled = EventHandled.create(
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                action = action,
            )

            // then
            assertThat(eventHandled.aggregateType).isEqualTo(aggregateType)
            assertThat(eventHandled.aggregateId).isEqualTo(aggregateId)
            assertThat(eventHandled.action).isEqualTo(action)
        }
    }

    @DisplayName("엔티티 필드 매핑")
    @Nested
    inner class FieldMapping {

        @DisplayName("영속화 전 id는 0이다")
        @Test
        fun `id is 0 before persistence`() {
            // given
            val aggregateType = "Like"
            val aggregateId = "product-456"
            val action = "createLike"

            // when
            val eventHandled = EventHandled.create(
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                action = action,
            )

            // then
            assertThat(eventHandled.id).isEqualTo(0L)
        }

        @DisplayName("handledAt은 자동으로 설정된다 (null이 아님)")
        @Test
        fun `handledAt is set automatically and not null`() {
            // given
            val aggregateType = "Order"
            val aggregateId = "123"
            val action = "completeOrder"

            // when
            val eventHandled = EventHandled.create(
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                action = action,
            )

            // then
            assertThat(eventHandled.handledAt).isNotNull()
        }

        @DisplayName("모든 필드가 올바르게 매핑된다")
        @Test
        fun `all fields are correctly mapped`() {
            // given
            val aggregateType = "Coupon"
            val aggregateId = "coupon-999"
            val action = "issueCoupon"

            // when
            val eventHandled = EventHandled.create(
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                action = action,
            )

            // then
            assertThat(eventHandled.id).isEqualTo(0L)
            assertThat(eventHandled.aggregateType).isEqualTo(aggregateType)
            assertThat(eventHandled.aggregateId).isEqualTo(aggregateId)
            assertThat(eventHandled.action).isEqualTo(action)
            assertThat(eventHandled.handledAt).isNotNull()
        }
    }
}
