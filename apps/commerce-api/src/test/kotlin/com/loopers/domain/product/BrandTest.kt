package com.loopers.domain.product

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test

class BrandTest {

    @Test
    fun `create 메서드로 Brand 객체를 생성할 수 있다`() {
        // given
        val name = "브랜드"

        // when
        val brand = Brand.create(
            name = name,
        )

        // then
        assertSoftly { softly ->
            softly.assertThat(brand.name).isEqualTo(name)
        }
    }
}
