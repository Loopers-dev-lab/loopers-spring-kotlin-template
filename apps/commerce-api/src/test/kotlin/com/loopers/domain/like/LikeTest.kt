package com.loopers.domain.like

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LikeTest {
    @Test
    fun `좋아요를 생성할 수 있다`() {
        // given
        val userId = 1L
        val productId = 100L

        // when
        val like = Like(userId = userId, productId = productId)

        // then
        assertThat(like.userId).isEqualTo(userId)
        assertThat(like.productId).isEqualTo(productId)
    }
}
