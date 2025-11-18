package com.loopers.domain.like

interface ProductLikeRepository {

    fun deleteByUserIdAndProductId(userId: Long, productId: Long): DeleteResult

    fun save(productLike: ProductLike): SaveResult

    sealed interface SaveResult {
        data object Created : SaveResult
        data object AlreadyExists : SaveResult
    }

    sealed interface DeleteResult {
        data object Deleted : DeleteResult
        data object NotExist : DeleteResult
    }
}
