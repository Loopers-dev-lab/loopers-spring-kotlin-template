package com.loopers.infrastructure.dataplatform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DataPlatformClientAdapterTest {

    private val adapter = DataPlatformClientAdapter()

    @DisplayName("sendOrderCompleted 테스트")
    @Nested
    inner class SendOrderCompleted {

        @DisplayName("주문 완료 이벤트 전송 시 true를 반환한다")
        @Test
        fun `returns true when sending order completed event`() {
            // given
            val orderId = 1L

            // when
            val result = adapter.sendOrderCompleted(orderId)

            // then
            assertThat(result).isTrue()
        }
    }

    @DisplayName("sendLikeCreated 테스트")
    @Nested
    inner class SendLikeCreated {

        @DisplayName("좋아요 생성 이벤트 전송 시 true를 반환한다")
        @Test
        fun `returns true when sending like created event`() {
            // given
            val userId = 1L
            val productId = 1L

            // when
            val result = adapter.sendLikeCreated(userId, productId)

            // then
            assertThat(result).isTrue()
        }
    }

    @DisplayName("sendLikeCanceled 테스트")
    @Nested
    inner class SendLikeCanceled {

        @DisplayName("좋아요 취소 이벤트 전송 시 true를 반환한다")
        @Test
        fun `returns true when sending like canceled event`() {
            // given
            val userId = 1L
            val productId = 1L

            // when
            val result = adapter.sendLikeCanceled(userId, productId)

            // then
            assertThat(result).isTrue()
        }
    }
}
