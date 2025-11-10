package com.loopers.domain.like

import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test

class ProductLikeTest {

    @Test
    fun `create 메서드로 ProductLike 객체를 생성할 수 있다`() {
        // given
        val productId = 1L
        val userId = 100L

        // when
        val productLike = ProductLike.create(
            productId = productId,
            userId = userId,
        )

        // then
        SoftAssertions.assertSoftly { softly ->
            softly.assertThat(productLike.productId).isEqualTo(1L)
            softly.assertThat(productLike.userId).isEqualTo(100L)
        }
    }
}
