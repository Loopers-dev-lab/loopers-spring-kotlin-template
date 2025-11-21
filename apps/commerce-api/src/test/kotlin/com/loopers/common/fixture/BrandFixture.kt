package com.loopers.common.fixture

import com.loopers.domain.brand.Brand
import io.mockk.every
import io.mockk.mockk

object BrandFixture {

    fun create(
        id: Long = 10L,
        name: String = "테스트브랜드",
    ): Brand {
        val brand = mockk<Brand>()
        every { brand.id } returns id
        every { brand.name } returns name
        return brand
    }
}
