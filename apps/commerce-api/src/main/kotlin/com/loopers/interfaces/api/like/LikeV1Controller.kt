package com.loopers.interfaces.api.like

import com.loopers.application.like.LikeFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/like/products")
class LikeV1Controller(
    private val likeFacade: LikeFacade,
) {

    @PostMapping("/{productId}")
    fun addLike(
        @RequestHeader("X-USER-ID") userId: Long,
        @PathVariable productId: Long,
    ): ApiResponse<Unit> {
        likeFacade.addLike(userId, productId)
        return ApiResponse.success(Unit)
    }

    @DeleteMapping("/{productId}")
    fun removeLike(
        @RequestHeader("X-USER-ID") userId: Long,
        @PathVariable productId: Long,
    ): ApiResponse<Unit> {
        likeFacade.removeLike(userId, productId)
        return ApiResponse.success(Unit)
    }
}
