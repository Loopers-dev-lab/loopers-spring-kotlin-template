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
    fun addLike(memberId: String, productId: Long): LikeInfo {
        return likeService.addLike(memberId, productId)
    }

    fun cancelLike(memberId: String, productId: Long) {
        likeService.cancelLike(memberId, productId)
    }

    @Transactional(readOnly = true)
    fun getMyLikes(memberId: String, pageable: Pageable): Page<LikeInfo> {
        return likeService.getMyLikes(memberId, pageable)
    }
}
