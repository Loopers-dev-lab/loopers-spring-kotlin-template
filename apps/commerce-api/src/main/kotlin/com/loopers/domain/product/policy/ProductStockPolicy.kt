package com.loopers.domain.product.policy

object ProductStockPolicy {
    object Quantity {
        const val MESSAGE = "영문 및 숫자 및 한글 100자 이내"
        const val MIN = 0
    }
}
