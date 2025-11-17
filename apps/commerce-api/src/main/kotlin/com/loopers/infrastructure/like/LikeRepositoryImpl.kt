package com.loopers.infrastructure.like

import com.loopers.domain.like.LikeModel
import com.loopers.domain.like.LikeRepository
import org.springframework.stereotype.Component

@Component
class LikeRepositoryImpl(private val likeJpaRepository: LikeJpaRepository) : LikeRepository {
    override fun findByUserIdAndProductId(
        userId: Long,
        productId: Long,
    ): LikeModel? = likeJpaRepository.findByUserIdAndProductId(userId, productId)

    override fun save(likeModel: LikeModel): LikeModel = likeJpaRepository.save(likeModel)

    override fun countByProductId(productId: Long): Long = likeJpaRepository.countByProductId(productId)

    override fun deleteByUserIdAndProductId(userId: Long, productId: Long) =
        likeJpaRepository.deleteByUserIdAndProductId(userId, productId)
}
