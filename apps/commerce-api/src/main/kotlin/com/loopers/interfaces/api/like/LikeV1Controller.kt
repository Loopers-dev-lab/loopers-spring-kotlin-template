package com.loopers.interfaces.api.like

import com.loopers.application.like.LikeFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.config.AuthUser
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/likes")
class LikeV1Controller(
    private val likeFacade: LikeFacade,
) : LikeV1ApiSpec {

    @PostMapping
    override fun addLike(
        authUser: AuthUser,
        @RequestBody request: LikeRequest.AddDto,
    ): ApiResponse<Unit> {
        likeFacade.addLike(
            userId = authUser.userId,
            productId = request.productId,
        )
        return ApiResponse.success(Unit)
    }

    @DeleteMapping
    override fun removeLike(
        authUser: AuthUser,
        @RequestBody request: LikeRequest.RemoveDto,
    ): ApiResponse<Unit> {
        likeFacade.removeLike(
            userId = authUser.userId,
            productId = request.productId,
        )
        return ApiResponse.success(Unit)
    }
}
