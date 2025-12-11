package com.loopers.interfaces.api.like

import com.loopers.application.like.LikeFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/likes")
class LikeController(private val likeFacade: LikeFacade) : LikeApiSpec {

    @PutMapping("/like")
    override fun like(request: LikeDto.Request): ApiResponse<LikeDto.Response> {
        likeFacade.like(request.userId, request.productId)
        return ApiResponse.success(LikeDto.Response.success())
    }

    @PutMapping("/unlike")
    override fun unlike(request: LikeDto.Request): ApiResponse<LikeDto.Response> {
        likeFacade.unlike(request.userId, request.productId)
        return ApiResponse.success(LikeDto.Response.success())
    }
}
