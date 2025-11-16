package com.loopers.interfaces.api.v1.product

import com.loopers.application.product.ProductFacade
import com.loopers.domain.product.ProductSort
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.dto.PageResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductV1Controller(
    private val productFacade: ProductFacade,
) : ProductV1ApiSpec {
    @GetMapping
    override fun getProducts(
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(defaultValue = "latest") sort: ProductSort,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<PageResponse<ProductV1Dto.ProductListResponse>> {
        val productPage = productFacade.getProducts(brandId, sort, pageable)

        return PageResponse.from(
            content = ProductV1Dto.ProductListResponse.from(productPage.content),
            page = productPage,
        ).let { ApiResponse.success(it) }
    }

    @GetMapping("/{productId}")
    override fun getProduct(
        @PathVariable productId: Long,
        @RequestHeader(value = "X-USER-ID", required = false) userId: String?,
    ): ApiResponse<ProductV1Dto.ProductDetailResponse> = productFacade.getProduct(productId, userId)
        .let { ProductV1Dto.ProductDetailResponse.from(it) }
        .let { ApiResponse.success(it) }
}
