package com.loopers.interfaces.api.product

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Product V1 API", description = "상품 관련 API 입니다.")
interface ProductV1ApiSpec {

    @Operation(summary = "상품 상세 조회")
    fun getProduct(
        productId: Long,
    ): ApiResponse<ProductResponse.ProductInfoDto>
}
