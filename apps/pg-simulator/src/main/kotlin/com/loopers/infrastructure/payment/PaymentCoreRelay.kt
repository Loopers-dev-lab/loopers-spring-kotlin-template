package com.loopers.infrastructure.payment

import com.loopers.application.payment.TransactionInfo
import com.loopers.domain.payment.PaymentRelay
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class PaymentCoreRelay : PaymentRelay {
    companion object {
        private val logger = LoggerFactory.getLogger(PaymentCoreRelay::class.java)
        private val restClient = RestClient.create()
    }

    override fun notify(callbackUrl: String, transactionInfo: TransactionInfo) {
        runCatching {
            restClient.post()
                .uri(callbackUrl)
                .body(transactionInfo)
                .retrieve()
                .toBodilessEntity()
        }.onFailure { e -> logger.error("콜백 호출을 실패했습니다. {}", e.message, e) }
    }
}
