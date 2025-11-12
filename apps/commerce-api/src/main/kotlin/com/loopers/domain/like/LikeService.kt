package com.loopers.domain.like

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeService(
    private val likeRepository: LikeRepository,
) {

    @Transactional(readOnly = true)
    fun countLikesByProductId(productId: Long): Long {
        return likeRepository.countByProductId(productId)
    }
}
