package com.loopers.infrastructure.dataplatform.client

import com.loopers.domain.event.OrderCreatedEvent
import com.loopers.domain.event.PaymentCompletedEvent

/**
 * 데이터 플랫폼 연동 클라이언트 인터페이스
 * 주문 및 결제 관련 데이터를 외부 데이터 플랫폼으로 전송
 */
interface DataPlatformClient {
    /**
     * 주문 생성 이벤트를 데이터 플랫폼으로 전송
     */
    fun sendOrderCreated(event: OrderCreatedEvent)

    /**
     * 결제 완료 이벤트를 데이터 플랫폼으로 전송
     */
    fun sendPaymentCompleted(event: PaymentCompletedEvent)
}
