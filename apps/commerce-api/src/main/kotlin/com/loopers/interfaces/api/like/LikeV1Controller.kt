package com.loopers.interfaces.api.like

import com.loopers.application.like.LikeFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class LikeV1Controller(
    private val likeFacade: LikeFacade
) : LikeV1ApiSpec {
    @PostMapping("/products/{productId}/likes")
    override fun addLike(
        @RequestHeader("X-USER-ID") userId: Long,
        @PathVariable(value = "productId") productId: Long
    ): ApiResponse<Unit> {
        likeFacade.addLike(userId, productId)
        return ApiResponse.success(Unit)
    }

    @DeleteMapping("/products/{productId}/likes")
    override fun removeLike(
        @RequestHeader("X-USER-ID") userId: Long,
        @PathVariable(value = "productId") productId: Long
    ): ApiResponse<Unit> {
        likeFacade.removeLike(userId, productId)
        return ApiResponse.success(Unit)
    }

    @GetMapping("/users/likes")
    override fun getLikedProducts(
        @RequestHeader("X-USER-ID") userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<Page<LikeV1Dto.LikedProductResponse>> {
        val pageable = PageRequest.of(page, size)
        return likeFacade.getLikedProducts(userId, pageable)
            .map { LikeV1Dto.LikedProductResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
