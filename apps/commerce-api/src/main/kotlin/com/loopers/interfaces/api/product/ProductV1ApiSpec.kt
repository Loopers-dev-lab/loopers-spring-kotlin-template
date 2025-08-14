package com.loopers.interfaces.api.product

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.product.request.ProductV1Request
import com.loopers.interfaces.api.product.response.ProductV1Response
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Product V1 API", description = "Product API 입니다.")
interface ProductV1ApiSpec {
    @Operation(
        summary = "상품 상세 조회",
        description = "상품 상세를 조회합니다.",
    )
    fun getProduct(productId: Long): ApiResponse<ProductV1Response.ProductResponse>

    @Operation(
        summary = "상품 목록 조회",
        description = "상품 목록을 조회합니다.",
    )
    fun getProducts(request: ProductV1Request.GetProducts): ApiResponse<ProductV1Response.ProductsResponse>
}
