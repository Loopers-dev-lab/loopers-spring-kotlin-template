package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductV1Controller(private val productFacade: ProductFacade) : ProductV1ApiSpec {
    @GetMapping
    override fun getProducts(
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(defaultValue = "latest") sort: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<Page<ProductV1Dto.ProductListResponse>> {
        val pageable = PageRequest.of(page, size)
        return productFacade.getProducts(brandId, sort, pageable)
            .map { ProductV1Dto.ProductListResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{productId}")
    override fun getProductDetail(
        @PathVariable(value = "productId") productId: Long,
    ): ApiResponse<ProductV1Dto.ProductDetailResponse> = productFacade.getProductDetail(productId)
        .let { ProductV1Dto.ProductDetailResponse.from(it) }
        .let { ApiResponse.success(it) }
}
