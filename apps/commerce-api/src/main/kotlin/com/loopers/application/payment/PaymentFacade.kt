package com.loopers.application.payment

import com.loopers.domain.payment.dto.command.PaymentCommand
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class PaymentFacade(
    private val paymentProcessor: PaymentProcessor,
) {
    @Transactional
    fun processPayment(command: PaymentCommand.Process) {
        paymentProcessor.process(command)
    }
}
