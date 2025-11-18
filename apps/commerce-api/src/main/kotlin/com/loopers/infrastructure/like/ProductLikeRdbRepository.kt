package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLike
import com.loopers.domain.like.ProductLikeRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Repository

@Repository
class ProductLikeRdbRepository(
    private val productLikeJpaRepository: ProductLikeJpaRepository,
) : ProductLikeRepository {
    override fun deleteByUserIdAndProductId(userId: Long, productId: Long): ProductLikeRepository.DeleteResult {
        val count = productLikeJpaRepository.deleteByUserIdAndProductId(userId, productId)
        return when (count) {
            0L -> ProductLikeRepository.DeleteResult.NotExist
            1L -> ProductLikeRepository.DeleteResult.Deleted
            else -> throw CoreException(ErrorType.INTERNAL_ERROR)
        }
    }

    override fun save(productLike: ProductLike): ProductLikeRepository.SaveResult {
        val insertCount = productLikeJpaRepository.trySave(productLike.productId, productLike.userId)
        return when (insertCount) {
            0 -> ProductLikeRepository.SaveResult.AlreadyExists
            1 -> ProductLikeRepository.SaveResult.Created
            else -> throw CoreException(ErrorType.INTERNAL_ERROR)
        }
    }
}
