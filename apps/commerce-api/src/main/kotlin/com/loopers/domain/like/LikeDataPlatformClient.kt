package com.loopers.domain.like

interface LikeDataPlatformClient {
    fun sendLikeCreated(userId: Long, productId: Long): Boolean
    fun sendLikeCanceled(userId: Long, productId: Long): Boolean
}
