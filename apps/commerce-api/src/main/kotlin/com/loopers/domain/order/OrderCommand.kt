package com.loopers.domain.order

import com.loopers.domain.brand.Brand
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentMethod
import com.loopers.domain.product.Product

object OrderCommand {

    data class Place(
        val userId: String,
        val items: List<OrderDetailCommand>,
        val couponId: Long?,
        val paymentMethod: PaymentMethod,
        val cardType: CardType? = null,
        val cardNo: String? = null,
    )

    data class Create(
        val userId: Long,
        val totalAmount: Long,
        val items: List<OrderDetailCommand>,
        val brands: List<Brand>,
        val products: List<Product>,
        val couponId: Long? = null,
        val status: OrderStatus = OrderStatus.PENDING,
    )

    data class OrderDetailCommand(
        val productId: Long,
        val quantity: Long,
    )
}
