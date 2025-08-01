package com.loopers.domain.brand.dto.result

import com.loopers.domain.brand.entity.Brand
import com.loopers.domain.common.PageResult
import org.springframework.data.domain.Page
import java.time.ZonedDateTime

class BrandResult {
    data class BrandDetail(
        val id: Long,
        val name: String,
        val description: String,
        val createdAt: ZonedDateTime,
        val updatedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(brand: Brand): BrandDetail {
                return BrandDetail(
                    brand.id,
                    brand.name.value,
                    brand.description.value,
                    brand.createdAt,
                    brand.updatedAt,
                )
            }
        }
    }

    data class BrandDetails(
        val brands: List<BrandDetail>,
    ) {
        companion object {
            fun from(brands: List<Brand>): BrandDetails {
                return BrandDetails(
                    brands.map { BrandDetail.from(it) },
                )
            }
        }
    }

    data class BrandPageDetails(
        val brands: PageResult<BrandDetail>,
    ) {
        companion object {
            fun from(brands: Page<Brand>): BrandPageDetails {
                return BrandPageDetails(
                    PageResult.from(brands) { BrandDetail.from(it) },
                )
            }
        }
    }
}
