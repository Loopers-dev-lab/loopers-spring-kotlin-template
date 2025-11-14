package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

enum class ProductSortType(
    val value: String,
) {
    LATEST("latest"),
    PRICE_ASC("price_asc"),
    LIKES_DESC("likes_desc"),
    ;

    companion object {
        private val map = entries.associateBy { it.value }

        fun from(value: String) = map[value.lowercase()]
            ?: throw CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 정렬 타입입니다.")
    }
}
