package com.loopers.application.order

import com.loopers.domain.order.OrderService
import com.loopers.domain.user.UserService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderFacade(
    private val orderService: OrderService,
    private val userService: UserService,
) {

    @Transactional(readOnly = true)
    fun getOrders(userId: String, pageable: Pageable): Page<OrderResult.ListInfo> {
        val user = userService.getMyInfo(userId)

        val orderPage = orderService.getOrders(user.id, pageable)

        return orderPage.map { OrderResult.ListInfo.from(it) }
    }

    @Transactional(readOnly = true)
    fun getOrder(userId: String, orderId: Long): OrderResult.DetailInfo {
        val user = userService.getMyInfo(userId)

        val order = orderService.getOrder(user.id, orderId)
        val orderDetail = orderService.getOrderDetail(orderId)

        return OrderResult.DetailInfo.from(order, orderDetail)
    }
}
