package com.loopers.domain.member

import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable

@Embeddable
data class Point(
    val amount: Long
) {
    init {
        require(amount >= 0) {
            throw InvalidPointAmountException(ErrorType.BAD_REQUEST, "포인트는 0 이상이어야 합니다.")
        }
    }
}
