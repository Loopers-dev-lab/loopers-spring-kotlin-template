package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class Quantity(
    @Column(name = "quantity", nullable = false)
    val value: Int,
) {
    init {
        if (value <= 0) {
            throw CoreException(ErrorType.INVALID_QUANTITY, "수량은 1 이상이어야 합니다. 입력값: $value")
        }
    }

    companion object {
        fun of(value: Int) = Quantity(value)
    }
}
