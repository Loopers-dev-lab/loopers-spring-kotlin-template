package com.loopers.domain.payment

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class PaymentService(

) {

    @Transactional
    fun request(command: PaymentCommand.Request) {

    }
}
