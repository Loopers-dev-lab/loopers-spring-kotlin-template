package com.loopers.interfaces.api.v1.brand

import com.loopers.domain.brand.BrandResult

class BrandV1Dto {
    data class BrandResponse(
        val id: Long,
        val name: String,
    ) {
        companion object {
            fun from(info: BrandResult): BrandResponse = BrandResponse(
                id = info.id,
                name = info.name,
            )
        }
    }
}
