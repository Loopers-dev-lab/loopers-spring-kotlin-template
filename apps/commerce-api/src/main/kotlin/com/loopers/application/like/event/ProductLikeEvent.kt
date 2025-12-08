package com.loopers.application.like.event

object ProductLikeEvent {
    /**
     * 좋아요가 추가되었을 때 발생하는 이벤트
     * 집계 처리를 비동기로 분리하기 위해 사용 (eventual consistency)
     */
    data class ProductLiked(
        val productId: Long,
        val userId: String,
    )

    /**
     * 좋아요가 취소되었을 때 발생하는 이벤트
     * 집계 감소를 비동기로 처리하기 위해 사용
     */
    data class ProductUnliked(
        val productId: Long,
        val userId: String,
    )
}
