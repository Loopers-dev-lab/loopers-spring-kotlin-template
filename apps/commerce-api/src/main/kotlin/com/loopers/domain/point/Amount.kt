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

    private fun validate(amount: Long) {
        require(amount > 0) { ERROR_MESSAGE_NOT_POSITIVE }
    }

    operator fun plus(other: Amount): Amount {
        return Amount(this.value + other.value)
    }

    companion object {
        private const val ERROR_MESSAGE_NOT_POSITIVE = "충전 금액은 0보다 커야 합니다."
    }
}
