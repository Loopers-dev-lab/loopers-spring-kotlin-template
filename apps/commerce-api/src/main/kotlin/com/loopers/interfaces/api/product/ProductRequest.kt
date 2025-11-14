package com.loopers.interfaces.api.product

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

class ProductRequest {

    data class CreateDto(
        @get:Schema(description = "상품명", example = "나이키 에어포스", required = true)
        val name: String,

        @get:Schema(description = "상품 가격", example = "129000", required = true)
        val price: BigDecimal,

        @get:Schema(description = "브랜드 ID", example = "1", required = true)
        val brandId: Long,
    )

    data class UpdateDto(
        @get:Schema(description = "상품명", example = "나이키 에어포스")
        val name: String?,

        @get:Schema(description = "상품 가격", example = "129000")
        val price: BigDecimal?,

        @get:Schema(description = "브랜드 ID", example = "1")
        val brandId: Long?,
    )
}
