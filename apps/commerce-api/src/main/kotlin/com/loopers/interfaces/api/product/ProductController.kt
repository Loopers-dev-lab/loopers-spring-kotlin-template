package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductFacade
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.viewModel.ProductViewModelService
import com.loopers.interfaces.api.ApiResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/products")
class ProductController(
    private val productViewModelService: ProductViewModelService,
    private val productService: ProductService,
    private val productFacade: ProductFacade,
) : ProductApiSpec {

    @GetMapping("/v1")
    override fun getProductsV1(
        @PageableDefault(size = 20) pageable: Pageable,
        @RequestParam(required = false) brandId: Long?,
    ): ApiResponse<ProductDto.PageResponse<ProductDto.ProductViewModelResponse>> {
        val page = productViewModelService.getProductViewModels(pageable, brandId)
        val response = ProductDto.PageResponse.from(page) { ProductDto.ProductViewModelResponse.from(it) }
        return ApiResponse.success(response)
    }

    @GetMapping("/v2")
    override fun getProductsV2(
        @PageableDefault(size = 20) pageable: Pageable,
        @RequestParam(required = false) brandId: Long?,
    ): ApiResponse<ProductDto.PageResponse<ProductDto.ProductInfoResponse>> {
        val page = productService.getProducts(pageable, brandId)
        val response = ProductDto.PageResponse.from(page) { ProductDto.ProductInfoResponse.from(it) }
        return ApiResponse.success(response)
    }

    @GetMapping("/{productId}")
    override fun getProductInfo(
        @PathVariable productId: Long,
        @RequestHeader("X-USER-ID") userId: Long?,
    ): ApiResponse<ProductDto.GetProduct> {
        return ApiResponse.success(ProductDto.GetProduct.from(productFacade.getProductDetail(productId, userId)))
    }
}
