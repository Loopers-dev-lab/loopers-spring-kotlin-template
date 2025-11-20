package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductCriteria
import com.loopers.application.product.ProductFacade
import com.loopers.domain.product.ProductSortType
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductV1Controller(
    private val productFacade: ProductFacade,
) {

    @GetMapping
    fun getProducts(
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(required = false) sort: ProductSortType?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<ProductV1Response.GetProducts> {
        val criteria = ProductCriteria.FindProducts(
            brandId = brandId,
            sort = sort,
            page = page,
            size = size,
        )
        return productFacade.findProducts(criteria)
            .let { ProductV1Response.GetProducts.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{productId}")
    fun getProduct(
        @PathVariable productId: Long,
    ): ApiResponse<ProductV1Response.GetProduct> {
        return productFacade.findProductById(productId)
            .let { ProductV1Response.GetProduct.from(it) }
            .let { ApiResponse.success(it) }
    }
}
