package com.loopers.application.like

import com.loopers.domain.like.LikeService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeFacade(
    private val likeService: LikeService,
) {
    fun addLike(memberId: Long, productId: Long): LikeInfo {
        val like = likeService.addLike(memberId, productId)
        return LikeInfo.from(like)
    }

    fun cancelLike(memberId: Long, productId: Long) {
        likeService.cancelLike(memberId, productId)
    }

    @Transactional(readOnly= true)
    fun getMyLikes(memberId: Long, pageable: Pageable): Page<LikeInfo> {
        val likes = likeService.getMyLikes(memberId, pageable)
        return likes.map { LikeInfo.from(it) }
    }
}
