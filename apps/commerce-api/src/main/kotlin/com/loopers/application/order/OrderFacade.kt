package com.loopers.application.order

import com.loopers.domain.order.OrderItemService
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.dto.command.OrderCommand
import com.loopers.domain.order.dto.result.OrderItemResult
import com.loopers.domain.order.dto.result.OrderResult.OrderDetail
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.dto.result.PaymentResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class OrderFacade(
    private val orderService: OrderService,
    private val orderItemService: OrderItemService,
    private val paymentService: PaymentService,
) {
    @Transactional
    fun requestOrder(command: OrderCommand.RequestOrder): OrderDetail {
        val order = orderService.request(command)
        val orderItems = orderItemService.register(command.toItemCommand(order.id))
        return OrderDetail.from(order, OrderItemResult.OrderItemDetails.from(orderItems))
    }

    @Transactional
    fun requestPayment(command: OrderCommand.RequestPayment): OrderDetail {
        val order = orderService.get(command.orderId)
        order.paymentRequest()

        val payment = paymentService.request(command.toPaymentCommand())
        return OrderDetail.from(order, PaymentResult.PaymentDetails.from(payment))
    }
}
