package com.loopers.domain.product

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.Test

@DisplayName("ProductViewedEventV1 테스트")
class ProductViewedEventV1Test {

    @DisplayName("create 팩토리 메서드 테스트")
    @Nested
    inner class Create {

        @DisplayName("userId와 함께 올바른 속성으로 이벤트가 생성된다")
        @Test
        fun `create() factory creates event with userId`() {
            // given
            val productId = 100L
            val userId = 1L

            // when
            val event = ProductViewedEventV1.create(productId, userId)

            // then
            assertThat(event.productId).isEqualTo(100L)
            assertThat(event.userId).isEqualTo(1L)
            assertThat(event.occurredAt).isNotNull()
        }

        @DisplayName("userId 없이 올바른 속성으로 이벤트가 생성된다")
        @Test
        fun `create() factory creates event without userId`() {
            // given
            val productId = 200L
            val userId: Long? = null

            // when
            val event = ProductViewedEventV1.create(productId, userId)

            // then
            assertThat(event.productId).isEqualTo(200L)
            assertThat(event.userId).isNull()
            assertThat(event.occurredAt).isNotNull()
        }
    }
}
