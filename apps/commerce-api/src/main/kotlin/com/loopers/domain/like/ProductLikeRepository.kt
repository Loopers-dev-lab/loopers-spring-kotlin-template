package com.loopers.domain.like

interface ProductLikeRepository {
    fun deleteByUserIdAndProductId(userId: Long, productId: Long): Long

    /**
     * ProductLike를 DB에 upsert(삽입 또는 업데이트)합니다.
     * 동일한 userId와 productId가 이미 존재하면 무시, 없으면 새로 삽입합니다.
     *
     * @param productLike upsert할 ProductLike 엔티티
     * @return 새로 삽입되면 1, 이미 존재해서 변경사항이 없으면 0
     */
    fun upsert(productLike: ProductLike): Int
}
