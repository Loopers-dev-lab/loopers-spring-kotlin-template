package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.common.vo.Money
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "order_items")
class OrderItemModel(
    @Column
    val refProductId: Long,

    @Column
    val quantity: Long,

    @Column
    val productPrice: Money,
) : BaseEntity()
