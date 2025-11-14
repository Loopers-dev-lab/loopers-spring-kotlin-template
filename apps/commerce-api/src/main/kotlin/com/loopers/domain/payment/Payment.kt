package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "loopers_payment")
class Payment(
    @Column(unique = true, nullable = false)
    val orderId: Long,

    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false, precision = 10, scale = 2)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PaymentStatus,
) : BaseEntity() {

    init {
        validateAmount(amount)
    }

    companion object {
        fun of(
            orderId: Long,
            userId: Long,
            amount: BigDecimal,
        ): Payment {
            return Payment(
                orderId = orderId,
                userId = userId,
                amount = amount,
                status = PaymentStatus.PENDING,
            )
        }
    }

    fun complete() {
        if (status != PaymentStatus.PENDING) {
            throw CoreException(ErrorType.BAD_REQUEST, "대기 중인 결제만 완료할 수 있습니다.")
        }
        this.status = PaymentStatus.COMPLETED
    }

    fun fail() {
        if (status != PaymentStatus.PENDING) {
            throw CoreException(ErrorType.BAD_REQUEST, "대기 중인 결제만 실패 처리할 수 있습니다.")
        }
        this.status = PaymentStatus.FAILED
    }

    fun cancel() {
        if (status != PaymentStatus.COMPLETED) {
            throw CoreException(ErrorType.BAD_REQUEST, "완료된 결제만 취소할 수 있습니다.")
        }
        this.status = PaymentStatus.CANCELLED
    }

    fun isCompleted(): Boolean {
        return status == PaymentStatus.COMPLETED
    }

    fun isCancellable(): Boolean {
        return status == PaymentStatus.COMPLETED
    }

    private fun validateAmount(amount: BigDecimal) {
        if (amount < BigDecimal.ZERO) {
            throw CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0 이상이어야 합니다.")
        }
    }

    enum class PaymentStatus(val description: String) {
        PENDING("대기"),
        COMPLETED("완료"),
        FAILED("실패"),
        CANCELLED("취소"),
    }
}
