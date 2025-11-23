package com.loopers.support.fixtures

import com.loopers.domain.like.ProductLike
import com.loopers.domain.like.ProductLikeCount
import java.time.ZonedDateTime

object ProductLikeFixtures {
    fun createProductLike(
        id: Long = 1L,
        productId: Long = 1L,
        userId: Long = 1L,
    ): ProductLike {
        return ProductLike.create(
            productId = productId,
            userId = userId,
        ).withId(id).withCreatedAt(ZonedDateTime.now())
    }

    fun createProductLikeCount(
        productId: Long,
        likeCount: Long,
    ): ProductLikeCount {
        return ProductLikeCount.create(
            productId = productId,
            likeCount = likeCount,
        )
    }
}
