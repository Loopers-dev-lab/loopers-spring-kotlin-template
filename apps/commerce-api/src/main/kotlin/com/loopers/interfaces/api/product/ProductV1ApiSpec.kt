package com.loopers.interfaces.api.product

import com.loopers.domain.product.ProductSortType
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Product V1 API", description = "Loopers Product API 입니다.")
interface ProductV1ApiSpec {
    @Operation(
        summary = "상품 목록 조회",
        description = "상품 목록을 페이지네이션으로 조회합니다. 브랜드 필터링 및 정렬 옵션을 제공합니다.",
    )
    fun getProducts(
        @Parameter(
            name = "brandId",
            description = "필터링할 브랜드 ID (선택)",
            required = false,
            `in` = ParameterIn.QUERY,
        )
        brandId: Long?,
        @Parameter(
            name = "sort",
            description = "정렬 방식 (LATEST, POPULAR) (선택, 기본값: LATEST)",
            required = false,
            `in` = ParameterIn.QUERY,
        )
        sort: ProductSortType?,
        @Parameter(
            name = "page",
            description = "페이지 번호 (0부터 시작) (선택, 기본값: 0)",
            required = false,
            `in` = ParameterIn.QUERY,
        )
        page: Int?,
        @Parameter(
            name = "size",
            description = "페이지 크기 (1~100) (선택, 기본값: 20)",
            required = false,
            `in` = ParameterIn.QUERY,
        )
        size: Int?,
    ): ApiResponse<ProductV1Response.GetProducts>

    @Operation(
        summary = "상품 상세 조회",
        description = "상품 ID로 상품 상세 정보를 조회합니다.",
    )
    fun getProduct(
        @Parameter(
            name = "productId",
            description = "조회할 상품 ID",
            required = true,
            `in` = ParameterIn.PATH,
        )
        productId: Long,
    ): ApiResponse<ProductV1Response.GetProduct>
}
