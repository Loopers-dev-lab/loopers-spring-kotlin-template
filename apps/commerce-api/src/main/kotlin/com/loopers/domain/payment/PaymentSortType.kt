package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

enum class PaymentSortType(
    val value: String,
) {
    CREATED_AT_ASC("created_at_asc"),
    CREATED_AT_DESC("created_at_desc"),
    ;

    companion object {
        private val map = entries.associateBy { it.value }

        fun from(value: String) = map[value.lowercase()]
            ?: throw CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 정렬 타입입니다.")
    }
}
