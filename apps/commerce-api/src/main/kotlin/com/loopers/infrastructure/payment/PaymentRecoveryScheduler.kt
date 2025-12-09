package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentRecoveryService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PaymentRecoveryScheduler(
    private val paymentRecoveryService: PaymentRecoveryService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    fun recoverPendingPayments() {
       paymentRecoveryService.recoverPendingPayments()
    }
}
