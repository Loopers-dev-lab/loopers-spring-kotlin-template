package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductV1Controller(
    private val productFacade: ProductFacade,
) : ProductV1ApiSpec {

    @GetMapping("/{productId}")
    override fun getProduct(@PathVariable productId: Long): ApiResponse<ProductResponse.ProductInfoDto> {
        return productFacade.getProductInfo(productId)
            .let { ApiResponse.success(ProductResponse.ProductInfoDto.from(it)) }
    }

    @PostMapping
    override fun createProduct(@RequestBody request: ProductRequest.CreateDto): ApiResponse<ProductResponse.ProductInfoDto> {
        return productFacade.createProduct(
            name = request.name,
            price = request.price,
            brandId = request.brandId,
        ).let { ApiResponse.success(ProductResponse.ProductInfoDto.from(it)) }
    }

    @PutMapping("/{productId}")
    override fun updateProduct(
        @PathVariable productId: Long,
        @RequestBody request: ProductRequest.UpdateDto,
    ): ApiResponse<ProductResponse.ProductInfoDto> {
        return productFacade.updateProduct(
            productId = productId,
            name = request.name,
            price = request.price,
            brandId = request.brandId,
        ).let { ApiResponse.success(ProductResponse.ProductInfoDto.from(it)) }
    }

    @DeleteMapping("/{productId}")
    override fun deleteProduct(@PathVariable productId: Long): ApiResponse<Unit> {
        productFacade.deleteProduct(productId)
        return ApiResponse.success(Unit)
    }
}
