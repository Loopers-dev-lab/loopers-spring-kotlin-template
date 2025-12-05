package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import com.loopers.domain.shared.Money
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "payments")
class Payment(
    orderId: Long,
    amount: Money,
    paymentMethod: PaymentMethod,
    transactionKey: String? = null,
    cardType: String? = null,
    cardNo: String? = null,
): BaseEntity() {

}
