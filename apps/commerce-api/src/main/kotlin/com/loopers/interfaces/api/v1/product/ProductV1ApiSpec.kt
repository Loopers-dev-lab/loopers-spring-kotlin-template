package com.loopers.interfaces.api.v1.product

import com.loopers.domain.product.ProductSort
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.dto.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable

@Tag(name = "Product V1 API", description = "상품 조회 API")
interface ProductV1ApiSpec {
    @Operation(
        summary = "상품 목록 조회",
        description = "브랜드 ID, 정렬 조건, 페이징을 통해 상품 목록을 조회합니다.",
    )
    fun getProducts(
        @Parameter(description = "브랜드 ID (optional)")
        brandId: Long?,
        @Parameter(description = "정렬 기준 (latest, price_asc, likes_desc)", schema = Schema(defaultValue = "latest"))
        sort: ProductSort,
        @Parameter(description = "페이지 번호 (0부터 시작)", schema = Schema(defaultValue = "0"))
        pageable: Pageable,
    ): ApiResponse<PageResponse<ProductV1Dto.ProductListResponse>>

    @Operation(
        summary = "상품 상세 조회",
        description = "상품 ID로 상품 상세 정보를 조회합니다. 로그인 사용자의 경우 좋아요 여부를 함께 조회합니다.",
    )
    fun getProduct(
        @Schema(description = "상품 ID")
        productId: Long,
        @Parameter(description = "사용자 ID (X-USER-ID 헤더)", required = false)
        userId: String?,
    ): ApiResponse<ProductV1Dto.ProductDetailResponse>
}
