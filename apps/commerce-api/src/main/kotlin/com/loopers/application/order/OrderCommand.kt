package com.loopers.application.order

import java.math.BigDecimal

data class OrderCommand(val orderItems: List<OrderItemCommand>, val cardType: String, val cardNo: String, val couponId: Long?)

data class OrderItemCommand(val productId: Long, val quantity: Long, val productPrice: BigDecimal)
