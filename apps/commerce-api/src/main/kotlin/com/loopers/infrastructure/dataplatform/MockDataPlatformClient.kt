package com.loopers.infrastructure.dataplatform

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MockDataPlatformClient : DataPlatformClient {

    private val logger = LoggerFactory.getLogger(DataPlatformClient::class.java)

    override fun sendOrderEvent(payload: OrderEventPayload) {
        logger.info("Mock sendOrderEvent payload=$payload")
        Thread.sleep(200)
    }
}
