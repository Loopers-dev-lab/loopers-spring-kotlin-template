package com.loopers.interfaces.api.brand

import com.loopers.application.brand.BrandInfo

class BrandV1Dto {
    data class BrandResponse(val id: Long, val name: String, val description: String?) {
        companion object {
            fun from(info: BrandInfo): BrandResponse = BrandResponse(
                id = info.id,
                name = info.name,
                description = info.description,
            )
        }
    }
}
