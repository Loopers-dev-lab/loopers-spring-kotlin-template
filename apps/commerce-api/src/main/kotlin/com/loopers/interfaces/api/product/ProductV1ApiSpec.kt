package com.loopers.interfaces.api.product

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page

@Tag(name = "Product V1 API", description = "상품 조회 API")
interface ProductV1ApiSpec {
    @Operation(
        summary = "상품 목록 조회",
        description = "상품 목록을 조회합니다. 브랜드 필터링 및 정렬을 지원합니다.",
    )
    fun getProducts(
        @Parameter(description = "브랜드 ID (선택)")
        brandId: Long?,
        @Parameter(description = "정렬 기준 (latest, price_asc, likes_desc)", schema = Schema(defaultValue = "latest"))
        sort: String,
        @Parameter(description = "페이지 번호", schema = Schema(defaultValue = "0"))
        page: Int,
        @Parameter(description = "페이지 크기", schema = Schema(defaultValue = "20"))
        size: Int,
    ): ApiResponse<Page<ProductV1Dto.ProductListResponse>>

    @Operation(
        summary = "상품 상세 조회",
        description = "상품 ID로 상품 상세 정보를 조회합니다.",
    )
    fun getProductDetail(
        @Schema(description = "상품 ID")
        productId: Long,
    ): ApiResponse<ProductV1Dto.ProductDetailResponse>
}
