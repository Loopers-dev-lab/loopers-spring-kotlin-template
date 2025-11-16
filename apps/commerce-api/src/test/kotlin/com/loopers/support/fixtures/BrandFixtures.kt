package com.loopers.support.fixtures

import com.loopers.domain.brand.Brand
import java.time.ZonedDateTime

object BrandFixtures {
    fun createBrand(
        id: Long = 1L,
        name: String = "name",
    ): Brand {
        return Brand.create(name = name)
            .withId(id)
            .withCreatedAt(ZonedDateTime.now())
    }
}
