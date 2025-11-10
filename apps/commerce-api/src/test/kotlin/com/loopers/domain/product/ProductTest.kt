package com.loopers.domain.product

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test

class ProductTest {

    @Test
    fun `create 메서드로 Product 객체를 생성할 수 있다`() {
        // given
        val name = "상품"
        val brandId = 1L

        // when
        val product = Product.create(
            name = name,
            brandId = brandId,
        )

        // then
        assertSoftly { softly ->
            softly.assertThat(product.name).isEqualTo(name)
            softly.assertThat(product.brandId).isEqualTo(brandId)
        }
    }
}
