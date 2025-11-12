package com.loopers.interfaces.api.product

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.common.PageRequestDto
import com.loopers.interfaces.api.common.PageResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Product V1 API", description = "상품 관련 API 입니다.")
interface ProductV1ApiSpec {

    @Operation(summary = "상품 상세 조회")
    fun getProduct(
        productId: Long,
    ): ApiResponse<ProductResponse.ProductInfoDto>

    @Operation(summary = "상품 등록")
    fun createProduct(
        request: ProductRequest.CreateDto,
    ): ApiResponse<ProductResponse.ProductInfoDto>

    @Operation(summary = "상품 수정")
    fun updateProduct(
        productId: Long,
        request: ProductRequest.UpdateDto,
    ): ApiResponse<ProductResponse.ProductInfoDto>

    @Operation(summary = "상품 삭제")
    fun deleteProduct(
        productId: Long,
    ): ApiResponse<Unit>

    @Operation(summary = "상품 리스트 조회 (검색)")
    fun getProducts(
        request: PageRequestDto,
    ): ApiResponse<PageResponseDto<ProductResponse.ProductInfoDto>>
}
