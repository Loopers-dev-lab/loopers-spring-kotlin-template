package com.loopers.domain.product

import com.loopers.domain.brand.Brand
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ProductTest {
    @Test
    fun `상품을 생성할 수 있다`() {
        // given
        val name = "운동화"
        val price = Price(amount = BigDecimal("100000"), currency = Currency.KRW)
        val brand = Brand(name = "나이키", description = "스포츠 브랜드")

        // when
        val product = Product(name = name, price = price, brand = brand)

        // then
        assertThat(product.name).isEqualTo(name)
        assertThat(product.price).isEqualTo(price)
        assertThat(product.brand).isEqualTo(brand)
    }

    @Test
    fun `상품명이 비어있으면 예외가 발생한다`() {
        // given
        val price = Price(amount = BigDecimal("100000"), currency = Currency.KRW)
        val brand = Brand(name = "나이키", description = "스포츠 브랜드")

        // when & then
        assertThatThrownBy {
            Product(name = "", price = price, brand = brand)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("상품명")
    }

    @Test
    fun `상품 가격을 변경할 수 있다`() {
        // given
        val brand = Brand(name = "나이키", description = "스포츠 브랜드")
        val product = Product(
            name = "운동화",
            price = Price(BigDecimal("100000"), Currency.KRW),
            brand = brand
        )
        val newPrice = Price(BigDecimal("150000"), Currency.KRW)

        // when
        product.updatePrice(newPrice)

        // then
        assertThat(product.price).isEqualTo(newPrice)
        assertThat(product.price.amount).isEqualTo(BigDecimal("150000"))
    }

    @Test
    fun `상품명을 변경할 수 있다`() {
        // given
        val brand = Brand(name = "나이키", description = "스포츠 브랜드")
        val product = Product(
            name = "운동화",
            price = Price(BigDecimal("100000"), Currency.KRW),
            brand = brand
        )

        // when
        product.updateName("새운동화")

        // then
        assertThat(product.name).isEqualTo("새운동화")
    }

    @Test
    fun `상품명을 빈 문자열로 변경하면 예외가 발생한다`() {
        // given
        val brand = Brand(name = "나이키", description = "스포츠 브랜드")
        val product = Product(
            name = "운동화",
            price = Price(BigDecimal("100000"), Currency.KRW),
            brand = brand
        )

        // when & then
        assertThatThrownBy {
            product.updateName("")
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("상품명")
    }
}
