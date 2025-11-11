package com.loopers.application.order

import com.loopers.domain.order.OrderService
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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
        // 1. 사용자 존재 여부 확인
        val user = userService.getMyInfo(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "유저를 찾을 수 없습니다: $userId")

        // 2. 유저의 주문 정보 조회
        val orderPage = orderService.getOrders(user.id, pageable)

        return orderPage.map { OrderResult.ListInfo.from(it) }
    }
}
