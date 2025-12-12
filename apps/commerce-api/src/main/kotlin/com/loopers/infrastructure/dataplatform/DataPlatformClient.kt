package com.loopers.infrastructure.dataplatform

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DataPlatformClient {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun sendOrderData(orderId: Long, memberId: String, amount: Long) {
        logger.info("데이터 플랫폼 전송 (더미): orderId=$orderId, memberId=$memberId, amount=$amount")

        Thread.sleep(100)
    }

    fun sendPaymentData(paymentId: Long, orderId: Long, memberId: String, amount: Long) {
        logger.info("데이터 플랫폼 전송 (더미): paymentId=$paymentId, orderId=$orderId, memberId=$memberId, amount=$amount")
        Thread.sleep(100)
    }
}
