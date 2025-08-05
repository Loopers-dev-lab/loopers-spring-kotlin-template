package com.loopers.domain.brand

import com.loopers.domain.brand.entity.Brand
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class BrandTest {
    @Test
    fun `브랜드를 정상적으로 등록한다`() {
        // when
        val brand = Brand.create("구찌", "비싸다")

        // then
        assertThat(brand.name.value).isEqualTo("구찌")
        assertThat(brand.description.value).isEqualTo("비싸다")
    }

    @Test
    fun `브랜드 이름이 비어 있으면 예외가 발생한다`() {
        // when & then
        assertThrows<CoreException> {
            Brand.create("", "LOOPERS")
        }
    }

    @Test
    fun `설명이 비어 있으면 예외가 발생한다`() {
        // when & then
        assertThrows<CoreException> {
            Brand.create("루퍼스", "")
        }
    }
}
