package com.loopers.domain.like

import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test

class ProductLikeCountTest {

    @Test
    fun `create 메서드로 ProductLike 객체를 생성할 수 있다`() {
        // given
        val productId = 1L
        val likeCount = 100L

        // when
        val productLikeCount = ProductLikeCount.create(
            productId = productId,
            likeCount = likeCount,
        )

        // then
        SoftAssertions.assertSoftly { softly ->
            softly.assertThat(productLikeCount.productId).isEqualTo(1L)
            softly.assertThat(productLikeCount.likeCount).isEqualTo(100L)
        }
    }
}
