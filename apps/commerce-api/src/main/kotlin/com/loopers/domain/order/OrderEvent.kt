package com.loopers.domain.order

object OrderEvent {
    /**
     * 주문이 생성되었을 때 발생하는 이벤트
     * 쿠폰 사용 후속 처리를 위해 사용
     */
    data class OrderCreated(
        val orderId: Long,
        val userId: Long,
        val couponId: Long?,
    )

    /**
     * 주문이 완료되었을 때 발생하는 이벤트
     * 데이터 플랫폼 전송 등의 후속 처리를 위해 사용
     */
    data class OrderCompleted(
        val orderId: Long,
        val userId: Long,
        val totalAmount: Long,
        val items: List<OrderDetail>,
    )

    /**
     * 주문이 실패했을 때 발생하는 이벤트
     * 쿠폰 롤백 등의 보상 트랜잭션을 위해 사용
     */
    data class OrderFailed(
        val orderId: Long,
        val userId: Long,
        val reason: String?,
    )
}
