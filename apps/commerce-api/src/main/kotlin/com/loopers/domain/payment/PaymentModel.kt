package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import com.loopers.domain.common.vo.Money
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "payments_and_transactions")
class PaymentModel(
    @Column
    val refOrderKey: String,

    @Column
    var transactionKey: String,

    @Column
    val amount: Money,

    @Column
    val cardType: String,

    @Column
    val cardNo: String,

    @Column
    var status: PaymentStatus = PaymentStatus.NOT_STARTED,
) : BaseEntity() {

    fun updateTransactionKey(transactionKey: String) {
        this.transactionKey = transactionKey
    }

    fun updateStatus(status: PaymentStatus) {
        this.status = status
    }

    companion object {
        fun create(
            refOrderKey: String,
            transactionKey: String,
            amount: Money,
            cardType: String,
            cardNo: String,
        ): PaymentModel =
            PaymentModel(refOrderKey, transactionKey, amount, cardType, cardNo)
    }
}
