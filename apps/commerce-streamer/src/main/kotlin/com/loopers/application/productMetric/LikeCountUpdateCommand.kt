package com.loopers.application.productMetric

data class LikeCountUpdateCommand(
        val likeCountGroupBy: Map<Long, Long>,
)
