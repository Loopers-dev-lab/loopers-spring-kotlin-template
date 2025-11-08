package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class BrandTest {
    @Test
    fun `브랜드를 생성할 수 있다`() {
        // given
        val name = "나이키"
        val description = "스포츠 브랜드"

        // when
        val brand = Brand(name = name, description = description)

        // then
        assertThat(brand.name).isEqualTo(name)
        assertThat(brand.description).isEqualTo(description)
    }

    @Test
    fun `이름이 비어있으면 예외가 발생한다`() {
        assertThatThrownBy {
            Brand(name = "", description = "설명")
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("이름")
    }

    @Test
    fun `설명이 null이어도 생성할 수 있다`() {
        // when
        val brand = Brand(name = "나이키", description = null)

        // then
        assertThat(brand.name).isEqualTo("나이키")
        assertThat(brand.description).isNull()
    }
}
