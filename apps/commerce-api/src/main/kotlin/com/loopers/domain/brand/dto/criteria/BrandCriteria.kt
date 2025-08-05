package com.loopers.domain.brand.dto.criteria

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

data class BrandCriteria(
    val sort: BrandSortType = BrandSortType.LATEST,
    val page: Int = 0,
    val size: Int = 20,
) {
    fun toPageRequest(): PageRequest {
        return PageRequest.of(page, size, sort.toSort())
    }

    enum class BrandSortType {
        LATEST,
        CREATED_AT_ASC,
        CREATED_AT_DESC,
        ;

        fun toSort(): Sort = when (this) {
            LATEST -> Sort.by(Sort.Direction.DESC, "createdAt")
            CREATED_AT_ASC -> Sort.by(Sort.Direction.ASC, "createdAt")
            CREATED_AT_DESC -> Sort.by(Sort.Direction.DESC, "createdAt")
        }
    }
}
