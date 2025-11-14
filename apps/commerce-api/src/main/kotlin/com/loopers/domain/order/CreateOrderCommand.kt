package com.loopers.domain.order

import java.math.BigDecimal

data class CreateOrderCommand(
    val productId: Long,
    val productName: String,
    val brandName: String,
    val price: BigDecimal,
    val quantity: Int,
)
