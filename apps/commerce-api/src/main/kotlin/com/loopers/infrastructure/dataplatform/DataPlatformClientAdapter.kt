package com.loopers.infrastructure.dataplatform

import com.loopers.domain.like.LikeDataPlatformClient
import com.loopers.domain.order.OrderDataPlatformClient
import org.springframework.stereotype.Component

@Component
class DataPlatformClientAdapter : OrderDataPlatformClient, LikeDataPlatformClient {

    override fun sendOrderCompleted(orderId: Long): Boolean {
        // TODO: Implement actual DataPlatform API call
        return true
    }

    override fun sendLikeCreated(userId: Long, productId: Long): Boolean {
        // TODO: Implement actual DataPlatform API call
        return true
    }

    override fun sendLikeCanceled(userId: Long, productId: Long): Boolean {
        // TODO: Implement actual DataPlatform API call
        return true
    }
}
