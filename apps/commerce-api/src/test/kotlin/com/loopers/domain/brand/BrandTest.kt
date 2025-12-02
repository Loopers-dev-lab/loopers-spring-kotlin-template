package com.loopers.domain.brand

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class BrandTest {

    @DisplayName("브랜드를 생성할 수 있다")
    @Test
    fun createBrand() {
        val brand = Brand("브랜드1", "브랜드 설명")

        assertAll (
            { assertThat(brand.name).isEqualTo("브랜드1") },
            { assertThat(brand.description).isEqualTo("브랜드 설명") }
        )
    }

}
