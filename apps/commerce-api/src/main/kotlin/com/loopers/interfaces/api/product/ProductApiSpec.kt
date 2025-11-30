package com.loopers.interfaces.api.product

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable

@Tag(name = "Product", description = "상품 API")
interface ProductApiSpec {

    @Operation(summary = "상품 목록 조회 v1 - ProductViewModel 기반", description = "ProductViewModel 테이블을 직접 조회하여 페이징 처리합니다.")
    fun getProductsV1(
        @Parameter(description = "페이징 정보 (page, size, sort)") pageable: Pageable,
        @Parameter(description = "브랜드 ID 필터 (선택)") brandId: Long?,
    ): ApiResponse<ProductDto.PageResponse<ProductDto.ProductViewModelResponse>>

    @Operation(summary = "상품 목록 조회 v2 - Join 기반", description = "여러 테이블을 조인하여 실시간으로 데이터를 조회합니다.")
    fun getProductsV2(
        @Parameter(description = "페이징 정보 (page, size, sort)") pageable: Pageable,
        @Parameter(description = "브랜드 ID 필터 (선택)") brandId: Long?,
    ): ApiResponse<ProductDto.PageResponse<ProductDto.ProductInfoResponse>>
}
