package com.loopers.interfaces.api.v1.like

import com.loopers.application.like.ProductLikeFacade
import com.loopers.application.product.ProductFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.dto.PageResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/like")
class LikeV1Controller(
    private val productLikeFacade: ProductLikeFacade,
    private val productFacade: ProductFacade,
) : LikeV1ApiSpec {
    @PostMapping("/products/{productId}")
    override fun likeProduct(
        @PathVariable productId: Long,
        @RequestHeader(value = "X-USER-ID") userId: String,
    ): ApiResponse<Unit> {
        productLikeFacade.like(productId, userId)
        return ApiResponse.success(Unit)
    }

    @DeleteMapping("/products/{productId}")
    override fun unlikeProduct(
        @PathVariable productId: Long,
        @RequestHeader(value = "X-USER-ID") userId: String,
    ): ApiResponse<Unit> {
        productLikeFacade.unlike(productId, userId)
        return ApiResponse.success(Unit)
    }

    @GetMapping("/products")
    override fun getLikedProducts(
        @RequestHeader(value = "X-USER-ID") userId: String,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<PageResponse<LikeV1Dto.LikedProductListResponse>> {
        val likedProductPage = productFacade.getLikedProducts(userId, pageable)

        return PageResponse.from(
            content = LikeV1Dto.LikedProductListResponse.from(likedProductPage.content),
            page = likedProductPage,
        ).let { ApiResponse.success(it) }
    }
}
