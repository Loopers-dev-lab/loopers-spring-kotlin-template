package com.loopers.application.order

import java.math.BigDecimal

data class OrderCommand(val orderItems: List<OrderItemCommand>)

data class OrderItemCommand(val productId: Long, val quantity: Long, val productPrice: BigDecimal)
