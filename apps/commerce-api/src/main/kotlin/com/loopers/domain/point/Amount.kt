package com.loopers.domain.point

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class Amount(
    @Column(name = "amount", nullable = false)
    val value: Long,
) {

    init {
        validate(value)
    }

    fun isLessThan(other: Amount): Boolean {
        return this.value < other.value
    }

    fun isZero(): Boolean {
        return this.value == 0L
    }

    private fun validate(amount: Long) {
        require(amount >= 0) { ERROR_MESSAGE_NOT_POSITIVE }
    }

    operator fun plus(other: Amount): Amount {
        return Amount(this.value + other.value)
    }

    operator fun minus(other: Amount): Amount {
        return Amount(this.value - other.value)
    }

    companion object {
        private const val ERROR_MESSAGE_NOT_POSITIVE = "금액은 0이상 이어야 합니다."
    }
}
