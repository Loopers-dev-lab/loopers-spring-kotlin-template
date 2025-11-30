package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "payments",
    indexes = [
        Index(name = "idx_order_id", columnList = "order_id"),
        Index(name = "idx_user_id", columnList = "user_id"),
        Index(name = "idx_transaction_key", columnList = "transaction_key", unique = true),
        Index(name = "idx_status", columnList = "status"),
    ],
)
class Payment(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "order_id", nullable = false)
    val orderId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    val paymentMethod: PaymentMethod,

    @Column(name = "amount", nullable = false)
    val amount: Long,

    @Column(name = "card_type", nullable = true, length = 20)
    val cardType: String? = null,

    @Column(name = "card_no", nullable = true, length = 20)
    val cardNo: String? = null,
) : BaseEntity() {
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: PaymentStatus = PaymentStatus.PENDING
        protected set

    @Column(name = "transaction_key", nullable = true, length = 100)
    var transactionKey: String? = null
        protected set

    @Column(name = "failure_reason", nullable = true, length = 500)
    var failureReason: String? = null
        protected set

    fun updateTransactionKey(transactionKey: String) {
        if (this.transactionKey != null) {
            throw CoreException(ErrorType.BAD_REQUEST, "이미 거래 키가 설정되었습니다.")
        }
        this.transactionKey = transactionKey
    }

    fun complete(reason: String? = null) {
        if (status == PaymentStatus.COMPLETED) {
            return // 멱등성
        }
        if (status == PaymentStatus.FAILED) {
            throw CoreException(ErrorType.BAD_REQUEST, "실패한 결제는 완료할 수 없습니다.")
        }
        this.status = PaymentStatus.COMPLETED
        this.failureReason = reason
    }

    fun fail(reason: String) {
        if (status == PaymentStatus.FAILED) {
            return // 멱등성
        }
        if (status == PaymentStatus.COMPLETED) {
            throw CoreException(ErrorType.BAD_REQUEST, "이미 완료된 결제는 실패 처리할 수 없습니다.")
        }
        this.status = PaymentStatus.FAILED
        this.failureReason = reason
    }

    fun timeout() {
        if (status != PaymentStatus.PENDING) {
            return // 이미 처리된 결제
        }
        this.status = PaymentStatus.TIMEOUT
        this.failureReason = "결제 시간이 초과되었습니다."
    }

    fun isPending(): Boolean = status == PaymentStatus.PENDING

    fun isCompleted(): Boolean = status == PaymentStatus.COMPLETED

    fun isFailed(): Boolean = status == PaymentStatus.FAILED || status == PaymentStatus.TIMEOUT
}
