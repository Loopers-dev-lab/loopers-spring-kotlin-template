package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class ExternalOrderService {

    fun processOrder(order: Order) {
        try {
            sendToExternalSystem(order)
        } catch (e: Exception) {
            throw CoreException(
                ErrorType.ORDER_PROCESSING_FAILED,
                "주문 처리 중 외부 시스템 연동 실패: ${e.message}"
            )
        }
    }

    private fun sendToExternalSystem(order: Order) {
        // Mock API 호출
        // 실제 구현에서는 RestTemplate, WebClient 등을 사용하여 외부 API 호출
    }
}
