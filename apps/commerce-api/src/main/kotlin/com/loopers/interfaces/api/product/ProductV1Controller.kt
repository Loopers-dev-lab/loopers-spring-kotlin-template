package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.product.request.ProductV1Request.GetProducts
import com.loopers.interfaces.api.product.response.ProductV1Response.ProductResponse
import com.loopers.interfaces.api.product.response.ProductV1Response.ProductsResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductV1Controller(
    private val productFacade: ProductFacade,
) : ProductV1ApiSpec {
    @GetMapping("/{productId}")
    override fun getProduct(@PathVariable productId: Long): ApiResponse<ProductResponse> {
        return productFacade.getProduct(productId)
            .let { ProductResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("")
    override fun getProducts(request: GetProducts): ApiResponse<ProductsResponse> {
        return productFacade.getProducts(request.toCriteria())
            .let { ProductsResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
