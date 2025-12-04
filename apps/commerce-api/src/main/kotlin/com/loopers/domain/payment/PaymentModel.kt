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
    val refUserId: Long,

    @Column
    var transactionKey: String,

    @Column
    val amount: Money,

    @Column
    var status: PaymentStatus = PaymentStatus.NOT_STARTED,
) : BaseEntity() {

    fun updateTransactionKey(transactionKey: String) {
        this.transactionKey = transactionKey
    }

    fun updateStatus(status: String) {
        if (status == "COMPLETE") {
            this.status = PaymentStatus.SUCCESS
        } else {
            this.status = PaymentStatus.FAIL
        }
    }

    companion object {
        fun create(refUserId: Long, refOrderKey: String, transactionKey: String, amount: Money): PaymentModel =
            PaymentModel(refOrderKey, refUserId, transactionKey, amount)
    }
}
