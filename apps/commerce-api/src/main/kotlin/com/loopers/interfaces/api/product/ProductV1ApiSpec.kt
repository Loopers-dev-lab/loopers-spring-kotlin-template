package com.loopers.interfaces.api.product

import com.loopers.domain.product.ProductSortType
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page

@Tag(name = "Product V1 API", description = "상품 관련 API 입니다.")
interface ProductV1ApiSpec {
    @Operation(
        summary = "상품 목록 조회",
        description = "상품 목록을 조회합니다. 브랜드 필터링, 정렬, 페이징을 지원합니다.",
    )
    fun getProducts(
        @Schema(name = "브랜드 ID (필터링)", description = "조회할 상품의 ID")
        brandId: Long?,
        @Schema(name = "정렬 기준", description = "정렬 기준 (latest, price_asc, likes_desc)")
        sort: ProductSortType,
        @Schema(name = "페이지 번호", description = "페이지 번호")
        page: Int,
        @Schema(name = "페이지 크기", description = "페이지 크기")
        size: Int,
    ): ApiResponse<Page<ProductV1Dto.ProductResponse>>

    @Operation(
        summary = "상품 상세 조회",
        description = "ID로 상품 상세 정보를 조회합니다."
    )
    fun getProduct(
        @Schema(name = "상품 ID", description = "조회할 상품의 ID")
        productId: Long,
    ) : ApiResponse<ProductV1Dto.ProductResponse>

}
